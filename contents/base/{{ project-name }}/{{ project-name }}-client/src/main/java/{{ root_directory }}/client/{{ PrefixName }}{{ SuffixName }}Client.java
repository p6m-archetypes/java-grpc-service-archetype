package {{ group_id }}.client;

import io.grpc.Channel;
import {{ group_id }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;
import {{ group_id }}.api.v1.HealthRequest;
import {{ group_id }}.api.v1.HealthResponse;

public class {{ PrefixName }}{{ SuffixName }}Client {

    private final {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}BlockingStub stub;

    public {{ PrefixName }}{{ SuffixName }}Client(Channel channel) {
        this.stub = {{ PrefixName }}{{ SuffixName }}Grpc.newBlockingStub(channel);
    }

    public HealthResponse health() {
        return stub.health(HealthRequest.newBuilder().build());
    }
}
