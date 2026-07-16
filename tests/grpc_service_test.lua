--- Acceptance suite for the Java gRPC service archetype: renders each persistence variant, verifies
--- the layout, builds the Maven reactor, boots the Spring Boot service against a real database
--- container, and proves the gRPC API answers (via reflection) while the service is wired to that
--- database. This suite defines the archetype's acceptance bar - its job is to fill the gaps and keep
--- them filled.
---
--- Run from the archetype repo root (uses ./prova.toml):   prova
--- requires docker + mvn + java (JDK 21); skips cleanly without them.
---
--- NOTE (why this matters): the archetype today is a SCAFFOLD - the proto exposes only a `Health` rpc
--- and its persistence module ships a single empty Flyway migration. prova *booting* the service and
--- driving gRPC against a real database is what proves "renders + compiles" is backed by a service
--- that starts, connects, and serves. As the archetype grows real CRUD, the assertions below graduate
--- from `Health` to real persisted state (Create -> row -> Get).

local postgres = require("postgres")
local mysql    = require("mysql")

local SRC = "."
local BOOT_JAR = "example-service-server/target/example-service-server-1.0.0-SNAPSHOT.jar"
-- prefix Example / suffix Service -> proto package example.service.v1, service ExampleService.
local HEALTH_RPC = "example.service.v1.ExampleService/Health"

local BASE_ANSWERS = {
  author_name      = "Test Author",
  author_email     = "test@example.com",
  org_name         = "acme",
  solution_name    = "platform",
  prefix_name      = "Example",
  suffix_name      = "Service",
  group_id         = "acme.platform",
  artifactory_host = "acme.jfrog.io",
  image_registry   = "ghcr.io/acme",
}

local function answers_with(extra)
  local out = {}
  for k, v in pairs(BASE_ANSWERS) do out[k] = v end
  for k, v in pairs(extra) do out[k] = v end
  return out
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

-- Build the reactor, then repackage the server into a runnable boot jar (see the REST suite's notes
-- on why install alone is a thin jar).
local function build(dir)
  shell.run("mvn -q -B -DskipTests install", { cwd = dir, timeout = "900s", check = true })
  shell.run("mvn -q -B -nsu -pl example-service-server -DskipTests package spring-boot:repackage",
    { cwd = dir, timeout = "900s", check = true })
end

local VARIANTS = {
  { persistence = "PostgreSQL", db = postgres },
  { persistence = "MySQL",      db = mysql },
}

for _, v in ipairs(VARIANTS) do
  local label = "java-grpc[" .. v.persistence .. "]"

  local project = prova.fixture(label .. ":project", Scope.File, function(ctx)
    return archetect.render{
      source = SRC,
      answers = answers_with{ persistence = v.persistence },
      destination = ctx:tempdir(),
      defaults = true,
    }
  end)

  local expected = {}
  for _, f in ipairs(BASE_FILES) do expected[#expected + 1] = f end
  for _, f in ipairs(PERSISTENCE_FILES) do expected[#expected + 1] = f end
  archetect.verify(project, {
    name = label,
    project_dir = "example-service",
    expected_files = expected,
    yaml_globs = { ".platform/kubernetes/**/*.yaml" },
  })

  local service = prova.fixture(label .. ":service", Scope.File, function(ctx)
    local root = ctx:use(project):dir("example-service")
    local db = v.db.container(ctx)

    build(root.path)

    -- gRPC is served on its own port; health/actuator on the management port.
    local grpc_port, mgmt = net.free_port(), net.free_port()
    ctx:manage(shell.spawn("java -jar " .. BOOT_JAR, {
      cwd = root.path,
      env = {
        GRPC_PORT              = grpc_port,
        MANAGEMENT_PORT        = mgmt,
        SPRING_PROFILES_ACTIVE = "persistence",
        DB_HOST                = db.host,
        DB_PORT                = db.port,
        DB_DBNAME              = "prova",
        DB_USERNAME            = "prova",
        DB_PASSWORD            = "prova",
      },
    }))

    local readiness = "http://127.0.0.1:" .. mgmt .. "/health/readiness"
    http.wait_for(readiness, { status = 200, timeout = "180s", every = "1s" })
    local addr = "127.0.0.1:" .. grpc_port
    grpc.wait_for(addr, { timeout = "60s" })
    return { readiness = readiness, addr = addr, db = db.client }
  end)

  prova.group(label .. " boots against " .. v.persistence, { requires = { "docker", "mvn", "java" } }, function(g)
    g:test("readiness reports UP", function(t)
      local svc = t:use(service)
      local res = http.get(svc.readiness)
      t:expect(res.status):equals(200)
      t:expect(res:json().status):equals("UP")
    end)

    g:test("gRPC Health rpc answers", function(t)
      local svc = t:use(service)
      local client = grpc.client(svc.addr)
      t:expect(client:call(HEALTH_RPC, {}).status):equals("OK")
    end)

    g:test("Flyway migrated the real " .. v.persistence .. " database", function(t)
      local svc = t:use(service)
      t:expect(svc.db:query_value("SELECT count(*) FROM flyway_schema_history"),
        "applied migrations"):gte(1)
    end)
  end)
end

-- The hollow rendering stays hollow: no persistence module, no scaffold files - and it still builds.
archetect.verify{
  name = "java-grpc[None]",
  source = SRC,
  answers = answers_with{ persistence = "None" },
  project_dir = "example-service",
  expected_files = BASE_FILES,
  absent_files = PERSISTENCE_FILES,
  yaml_globs = { ".platform/kubernetes/**/*.yaml" },
  requires = { "mvn" },
  build_steps = { "mvn -q -B -DskipTests install" },
}
