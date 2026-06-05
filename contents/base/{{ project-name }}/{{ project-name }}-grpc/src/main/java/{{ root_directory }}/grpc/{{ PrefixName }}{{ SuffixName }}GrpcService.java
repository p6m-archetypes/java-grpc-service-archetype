package {{ group_id }}.grpc;

import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import {{ group_id }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;
import {{ group_id }}.api.v1.HealthRequest;
import {{ group_id }}.api.v1.HealthResponse;

@GRpcService
public class {{ PrefixName }}{{ SuffixName }}GrpcService extends {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}ImplBase {

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        responseObserver.onNext(HealthResponse.newBuilder().setStatus("OK").build());
        responseObserver.onCompleted();
    }
}
