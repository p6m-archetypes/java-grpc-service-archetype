package {{ root_package }}.grpc;

{% if has_persistence %}
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import {{ group_id }}.persistence.{{ EntityName }};
import {{ group_id }}.persistence.{{ EntityName }}Repository;
import {{ root_package }}.api.v1.Create{{ EntityName }}Request;
import {{ root_package }}.api.v1.Delete{{ EntityName }}Request;
import {{ root_package }}.api.v1.Delete{{ EntityName }}Response;
import {{ root_package }}.api.v1.Get{{ EntityName }}Request;
import {{ root_package }}.api.v1.List{{ EntityName }}sRequest;
import {{ root_package }}.api.v1.List{{ EntityName }}sResponse;
import {{ root_package }}.api.v1.Update{{ EntityName }}Request;
import {{ root_package }}.api.v1.{{ EntityName }};
import {{ root_package }}.api.v1.{{ ProjectName }}Grpc;

/**
 * The standard CRUD surface (p6m standards S2) over the {@code { id, display_name }} entity,
 * backed by the persistence module. Unknown ids answer {@code NOT_FOUND}.
 */
@GRpcService
public class {{ ProjectName }}GrpcService extends {{ ProjectName }}Grpc.{{ ProjectName }}ImplBase {

    private final {{ EntityName }}Repository repository;

    public {{ ProjectName }}GrpcService({{ EntityName }}Repository repository) {
        this.repository = repository;
    }

    private static {{ EntityName }} toProto({{ EntityName }} item) {
        return {{ EntityName }}.newBuilder()
                .setId(item.getId())
                .setDisplayName(item.getDisplayName())
                .build();
    }

    private static StatusRuntimeException notFound(String id) {
        return Status.NOT_FOUND.withDescription("no entity with id " + id).asRuntimeException();
    }

    @Override
    public void create{{ EntityName }}(Create{{ EntityName }}Request request, StreamObserver<{{ EntityName }}> responseObserver) {
        {{ EntityName }} saved = repository.save(new {{ EntityName }}(request.getDisplayName()));
        responseObserver.onNext(toProto(saved));
        responseObserver.onCompleted();
    }

    @Override
    public void get{{ EntityName }}(Get{{ EntityName }}Request request, StreamObserver<{{ EntityName }}> responseObserver) {
        repository.findById(request.getId()).ifPresentOrElse(
                item -> {
                    responseObserver.onNext(toProto(item));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(notFound(request.getId())));
    }

    @Override
    public void list{{ EntityName }}s(List{{ EntityName }}sRequest request, StreamObserver<List{{ EntityName }}sResponse> responseObserver) {
        List{{ EntityName }}sResponse.Builder response = List{{ EntityName }}sResponse.newBuilder();
        repository.findAll().forEach(item -> response.addItems(toProto(item)));
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void update{{ EntityName }}(Update{{ EntityName }}Request request, StreamObserver<{{ EntityName }}> responseObserver) {
        repository.findById(request.getId()).ifPresentOrElse(
                item -> {
                    item.setDisplayName(request.getDisplayName());
                    responseObserver.onNext(toProto(repository.save(item)));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(notFound(request.getId())));
    }

    @Override
    public void delete{{ EntityName }}(Delete{{ EntityName }}Request request, StreamObserver<Delete{{ EntityName }}Response> responseObserver) {
        if (!repository.existsById(request.getId())) {
            responseObserver.onError(notFound(request.getId()));
            return;
        }
        repository.deleteById(request.getId());
        responseObserver.onNext(Delete{{ EntityName }}Response.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
{% else %}
import org.lognet.springboot.grpc.GRpcService;
import {{ root_package }}.api.v1.{{ ProjectName }}Grpc;

/**
 * The standard API surface (p6m standards S2), unimplemented until a persistence flavor backs it:
 * every rpc answers {@code UNIMPLEMENTED} from the generated base class. Standard gRPC health
 * ({@code grpc.health.v1.Health}) and server reflection are served by the runtime regardless.
 */
@GRpcService
public class {{ ProjectName }}GrpcService extends {{ ProjectName }}Grpc.{{ ProjectName }}ImplBase {
}
{% endif %}
