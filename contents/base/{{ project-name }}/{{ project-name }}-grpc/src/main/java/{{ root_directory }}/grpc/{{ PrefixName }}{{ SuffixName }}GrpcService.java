package {{ root_package }}.grpc;

{% if has_persistence %}
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import {{ group_id }}.persistence.Item;
import {{ group_id }}.persistence.ItemRepository;
import {{ root_package }}.api.v1.Create{{ PrefixName }}Request;
import {{ root_package }}.api.v1.Delete{{ PrefixName }}Request;
import {{ root_package }}.api.v1.Delete{{ PrefixName }}Response;
import {{ root_package }}.api.v1.Get{{ PrefixName }}Request;
import {{ root_package }}.api.v1.List{{ PrefixName }}sRequest;
import {{ root_package }}.api.v1.List{{ PrefixName }}sResponse;
import {{ root_package }}.api.v1.Update{{ PrefixName }}Request;
import {{ root_package }}.api.v1.{{ PrefixName }};
import {{ root_package }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;

/**
 * The standard CRUD surface (p6m standards S2) over the {@code { id, display_name }} entity,
 * backed by the persistence module. Unknown ids answer {@code NOT_FOUND}.
 */
@GRpcService
public class {{ PrefixName }}{{ SuffixName }}GrpcService extends {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}ImplBase {

    private final ItemRepository repository;

    public {{ PrefixName }}{{ SuffixName }}GrpcService(ItemRepository repository) {
        this.repository = repository;
    }

    private static {{ PrefixName }} toProto(Item item) {
        return {{ PrefixName }}.newBuilder()
                .setId(item.getId())
                .setDisplayName(item.getDisplayName())
                .build();
    }

    private static StatusRuntimeException notFound(String id) {
        return Status.NOT_FOUND.withDescription("no entity with id " + id).asRuntimeException();
    }

    @Override
    public void create{{ PrefixName }}(Create{{ PrefixName }}Request request, StreamObserver<{{ PrefixName }}> responseObserver) {
        Item saved = repository.save(new Item(request.getDisplayName()));
        responseObserver.onNext(toProto(saved));
        responseObserver.onCompleted();
    }

    @Override
    public void get{{ PrefixName }}(Get{{ PrefixName }}Request request, StreamObserver<{{ PrefixName }}> responseObserver) {
        repository.findById(request.getId()).ifPresentOrElse(
                item -> {
                    responseObserver.onNext(toProto(item));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(notFound(request.getId())));
    }

    @Override
    public void list{{ PrefixName }}s(List{{ PrefixName }}sRequest request, StreamObserver<List{{ PrefixName }}sResponse> responseObserver) {
        List{{ PrefixName }}sResponse.Builder response = List{{ PrefixName }}sResponse.newBuilder();
        repository.findAll().forEach(item -> response.addItems(toProto(item)));
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void update{{ PrefixName }}(Update{{ PrefixName }}Request request, StreamObserver<{{ PrefixName }}> responseObserver) {
        repository.findById(request.getId()).ifPresentOrElse(
                item -> {
                    item.setDisplayName(request.getDisplayName());
                    responseObserver.onNext(toProto(repository.save(item)));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(notFound(request.getId())));
    }

    @Override
    public void delete{{ PrefixName }}(Delete{{ PrefixName }}Request request, StreamObserver<Delete{{ PrefixName }}Response> responseObserver) {
        if (!repository.existsById(request.getId())) {
            responseObserver.onError(notFound(request.getId()));
            return;
        }
        repository.deleteById(request.getId());
        responseObserver.onNext(Delete{{ PrefixName }}Response.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
{% else %}
import org.lognet.springboot.grpc.GRpcService;
import {{ root_package }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;

/**
 * The standard API surface (p6m standards S2), unimplemented until a persistence flavor backs it:
 * every rpc answers {@code UNIMPLEMENTED} from the generated base class. Standard gRPC health
 * ({@code grpc.health.v1.Health}) and server reflection are served by the runtime regardless.
 */
@GRpcService
public class {{ PrefixName }}{{ SuffixName }}GrpcService extends {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}ImplBase {
}
{% endif %}
