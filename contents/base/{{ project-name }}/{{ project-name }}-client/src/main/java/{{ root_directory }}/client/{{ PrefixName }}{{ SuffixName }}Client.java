package {{ root_package }}.client;

import io.grpc.Channel;
import {{ root_package }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;
import {{ root_package }}.api.v1.HealthRequest;
import {{ root_package }}.api.v1.HealthResponse;

public class {{ PrefixName }}{{ SuffixName }}Client {

    private final {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}BlockingStub stub;

    public {{ PrefixName }}{{ SuffixName }}Client(Channel channel) {
        this.stub = {{ PrefixName }}{{ SuffixName }}Grpc.newBlockingStub(channel);
    }

    public HealthResponse health() {
        return stub.health(HealthRequest.newBuilder().build());
    }
}
