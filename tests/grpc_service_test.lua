--- Acceptance suite for the Java gRPC service archetype: renders each persistence variant, verifies
--- the layout, builds the Maven reactor, boots the Spring Boot service against a real database
--- container, and proves the gRPC API answers (via reflection) while the service is wired to that
--- database. This suite defines the archetype's acceptance bar - its job is to fill the gaps and keep
--- them filled.
---
--- Run from the archetype repo root (uses ./prova.toml):   prova
--- requires archetect + docker + mvn + java (JDK 21); skips cleanly without them.
---
--- NOTE (why this matters): the archetype today is a SCAFFOLD - the proto exposes only a `Health` rpc
--- and its persistence module ships a single empty Flyway migration. prova *booting* the service and
--- driving gRPC against a real database is what proves "renders + compiles" is backed by a service
--- that starts, connects, and serves. As the archetype grows real CRUD, the assertions below graduate
--- from `Health` to real persisted state (Create -> row -> Get).
---
--- Rendering shells out to the `archetect` CLI (one fresh process per render) rather than the
--- in-process `archetect.render`, which can only render once per process.

local SRC         = "."   -- shell.run inherits prova's cwd (the repo root), where the archetype lives
local PROJECT_DIR = "example-service"
local BOOT_JAR    = "example-service-server/target/example-service-server-1.0.0-SNAPSHOT.jar"
-- prefix Example / suffix Service -> proto package example.service.v1, service ExampleService.
local HEALTH_RPC  = "example.service.v1.ExampleService/Health"

local function answers_yaml(persistence)
  return table.concat({
    'author_name: "Test Author"',
    'author_email: "test@example.com"',
    'org_name: "acme"',
    'solution_name: "platform"',
    'prefix_name: "Example"',
    'suffix_name: "Service"',
    'group_id: "acme.platform"',
    'artifactory_host: "acme.jfrog.io"',
    'image_registry: "ghcr.io/acme"',
    'persistence: "' .. persistence .. '"',
  }, "\n") .. "\n"
end

local function render(ctx, persistence)
  local out     = ctx:tempdir()
  local answers = out .. "/answers.yaml"
  fs.write(answers, answers_yaml(persistence))
  shell.run("archetect render " .. SRC .. " " .. out .. "/rendered -A " .. answers .. " -D --headless",
    { timeout = "180s", check = true })
  return out .. "/rendered/" .. PROJECT_DIR
end

-- Build the reactor, then repackage the server into a runnable boot jar (see the REST suite's notes
-- on why install alone is a thin jar).
local function build(root)
  shell.run("mvn -q -B -DskipTests install", { cwd = root, timeout = "900s", check = true })
  shell.run("mvn -q -B -o -pl example-service-server -DskipTests package spring-boot:repackage",
    { cwd = root, timeout = "900s", check = true })
end

local PERSISTENCE_FILES = {
  "example-service-persistence/pom.xml",
  "example-service-persistence/src/main/java/acme/platform/example/persistence/PersistenceConfig.java",
  "example-service-persistence/src/main/resources/db/migration/V1__init.sql",
  "example-service-server/src/main/resources/application-persistence.yaml",
}

-- Base + gRPC api/grpc/client modules; present in every rendering.
local BASE_FILES = {
  "pom.xml",
  "example-service-bom/pom.xml",
  "example-service-core/pom.xml",
  "example-service-server/pom.xml",
  "example-service-server/src/main/java/acme/platform/example/server/Application.java",
  "example-service-server/src/main/resources/application.yaml",
  "example-service-integration-tests/pom.xml",
  "example-service-api/pom.xml",
  "example-service-api/src/main/proto/example_service.proto",
  "example-service-grpc/pom.xml",
  "example-service-grpc/src/main/java/acme/platform/example/grpc/ExampleServiceGrpcService.java",
  "example-service-client/pom.xml",
  "example-service-client/src/main/java/acme/platform/example/client/ExampleServiceClient.java",
  ".github/workflows/build.yaml",
}

local function check_layout(t, root, present, absent)
  t:expect_all(function()
    for _, f in ipairs(present) do
      t:expect(fs.exists(root .. "/" .. f), f):is_true()
    end
    for _, f in ipairs(absent or {}) do
      t:expect(fs.exists(root .. "/" .. f), f .. " (should be absent)"):is_false()
    end
  end)
end

local function check_yaml_manifests(t, root)
  local matches = fs.glob(root, ".platform/kubernetes/**/*.yaml")
  t:expect(#matches, "kubernetes manifests"):never():equals(0)
  for _, path in ipairs(matches) do
    yaml.parse_all(fs.read(path))
  end
end

local VARIANTS = {
  { persistence = "PostgreSQL", recipe = db.postgres, db_port = 5432 },
  { persistence = "MySQL",      recipe = db.mysql,    db_port = 3306 },
}

for _, v in ipairs(VARIANTS) do
  local label = "java-grpc[" .. v.persistence .. "]"

  local project = prova.fixture(label .. ":project", Scope.File, function(ctx)
    return render(ctx, v.persistence)
  end)

  prova.group(label, { requires = { "archetect" } }, function(g)
    g:test("layout includes the persistence module", function(t)
      local root = t:use(project)
      local present = {}
      for _, f in ipairs(BASE_FILES) do present[#present + 1] = f end
      for _, f in ipairs(PERSISTENCE_FILES) do present[#present + 1] = f end
      check_layout(t, root, present)
    end)

    g:test("platform kubernetes manifests parse", function(t)
      check_yaml_manifests(t, t:use(project))
    end)
  end)

  local service = prova.fixture(label .. ":service", Scope.File, function(ctx)
    local root = ctx:use(project)
    local d    = v.recipe(ctx, { database = "prova" })

    build(root)

    -- gRPC is served on its own port; health/actuator on the management port.
    local grpc_port, mgmt = net.free_port(), net.free_port()
    ctx:manage(shell.spawn("java -jar " .. BOOT_JAR, {
      cwd = root,
      env = {
        GRPC_PORT              = tostring(grpc_port),
        MANAGEMENT_PORT        = tostring(mgmt),
        SPRING_PROFILES_ACTIVE = "persistence",
        DB_HOST                = "127.0.0.1",
        DB_PORT                = tostring(d.container:host_port(v.db_port)),
        DB_DBNAME              = "prova",
        DB_USERNAME            = "prova",
        DB_PASSWORD            = "prova",
      },
    }))

    local readiness = "http://127.0.0.1:" .. mgmt .. "/health/readiness"
    http.wait_for(readiness, { status = 200, timeout = "180s", every = "1s" })
    local addr = "127.0.0.1:" .. grpc_port
    grpc.wait_for(addr, { timeout = "60s" })
    return { readiness = readiness, addr = addr, conn = d.conn }
  end)

  prova.group(label .. " boots against " .. v.persistence,
    { requires = { "archetect", "docker", "mvn", "java" } }, function(g)
    g:test("readiness reports UP", function(t)
      local svc = t:use(service)
      local res = http.get(svc.readiness)
      t:expect(res.status):equals(200)
      t:expect(res:json().status):equals("UP")
    end)

    g:test("gRPC Health rpc answers", function(t)
      local svc = t:use(service)
      local client = grpc.connect(svc.addr)
      t:expect(client:call(HEALTH_RPC, {}).status):equals("OK")
    end)

    g:test("Flyway migrated the real " .. v.persistence .. " database", function(t)
      local svc = t:use(service)
      t:expect(svc.conn:query_value("SELECT count(*) FROM flyway_schema_history"),
        "applied migrations"):gte(1)
    end)
  end)
end

-- The hollow rendering stays hollow: no persistence module, no scaffold files - and it still builds.
local none = prova.fixture("java-grpc[None]:project", Scope.File, function(ctx)
  return render(ctx, "None")
end)

prova.group("java-grpc[None]", { requires = { "archetect" } }, function(g)
  g:test("layout omits the persistence module", function(t)
    local root = t:use(none)
    check_layout(t, root, BASE_FILES, PERSISTENCE_FILES)
  end)

  g:test("platform kubernetes manifests parse", function(t)
    check_yaml_manifests(t, t:use(none))
  end)

  g:test("builds", { requires = { "archetect", "mvn", "java" }, timeout = "900s" }, function(t)
    local root = t:use(none)
    local r = shell.run("mvn -q -B -DskipTests install", { cwd = root, timeout = "900s" })
    t:expect(r.code, "mvn install"):equals(0)
  end)
end)
