package {{ root_package }}.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan the whole solution package so beans in sibling modules (grpc, persistence) are picked up,
// not just those under the server package.
@SpringBootApplication(scanBasePackages = "{{ root_package }}")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
