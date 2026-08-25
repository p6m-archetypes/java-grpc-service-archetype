package {{ root_package }}.client;

import io.grpc.Channel;
import {{ root_package }}.api.v1.Create{{ EntityName }}Request;
import {{ root_package }}.api.v1.Delete{{ EntityName }}Request;
import {{ root_package }}.api.v1.Get{{ EntityName }}Request;
import {{ root_package }}.api.v1.List{{ EntityName }}sRequest;
import {{ root_package }}.api.v1.List{{ EntityName }}sResponse;
import {{ root_package }}.api.v1.Update{{ EntityName }}Request;
import {{ root_package }}.api.v1.{{ EntityName }};
import {{ root_package }}.api.v1.{{ ProjectName }}Grpc;

/** Thin blocking client over the standard CRUD surface (p6m standards S2). */
public class {{ ProjectName }}Client {

    private final {{ ProjectName }}Grpc.{{ ProjectName }}BlockingStub stub;

    public {{ ProjectName }}Client(Channel channel) {
        this.stub = {{ ProjectName }}Grpc.newBlockingStub(channel);
    }

    public {{ EntityName }} create(String displayName) {
        return stub.create{{ EntityName }}(Create{{ EntityName }}Request.newBuilder().setDisplayName(displayName).build());
    }

    public {{ EntityName }} get(String id) {
        return stub.get{{ EntityName }}(Get{{ EntityName }}Request.newBuilder().setId(id).build());
    }

    public List{{ EntityName }}sResponse list() {
        return stub.list{{ EntityName }}s(List{{ EntityName }}sRequest.getDefaultInstance());
    }

    public {{ EntityName }} update(String id, String displayName) {
        return stub.update{{ EntityName }}(Update{{ EntityName }}Request.newBuilder().setId(id).setDisplayName(displayName).build());
    }

    public void delete(String id) {
        stub.delete{{ EntityName }}(Delete{{ EntityName }}Request.newBuilder().setId(id).build());
    }
}
