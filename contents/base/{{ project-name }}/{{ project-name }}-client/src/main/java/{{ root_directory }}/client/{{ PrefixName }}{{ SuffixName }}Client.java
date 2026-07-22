package {{ root_package }}.client;

import io.grpc.Channel;
import {{ root_package }}.api.v1.Create{{ PrefixName }}Request;
import {{ root_package }}.api.v1.Delete{{ PrefixName }}Request;
import {{ root_package }}.api.v1.Get{{ PrefixName }}Request;
import {{ root_package }}.api.v1.List{{ PrefixName }}sRequest;
import {{ root_package }}.api.v1.List{{ PrefixName }}sResponse;
import {{ root_package }}.api.v1.Update{{ PrefixName }}Request;
import {{ root_package }}.api.v1.{{ PrefixName }};
import {{ root_package }}.api.v1.{{ PrefixName }}{{ SuffixName }}Grpc;

/** Thin blocking client over the standard CRUD surface (p6m standards S2). */
public class {{ PrefixName }}{{ SuffixName }}Client {

    private final {{ PrefixName }}{{ SuffixName }}Grpc.{{ PrefixName }}{{ SuffixName }}BlockingStub stub;

    public {{ PrefixName }}{{ SuffixName }}Client(Channel channel) {
        this.stub = {{ PrefixName }}{{ SuffixName }}Grpc.newBlockingStub(channel);
    }

    public {{ PrefixName }} create(String displayName) {
        return stub.create{{ PrefixName }}(Create{{ PrefixName }}Request.newBuilder().setDisplayName(displayName).build());
    }

    public {{ PrefixName }} get(String id) {
        return stub.get{{ PrefixName }}(Get{{ PrefixName }}Request.newBuilder().setId(id).build());
    }

    public List{{ PrefixName }}sResponse list() {
        return stub.list{{ PrefixName }}s(List{{ PrefixName }}sRequest.getDefaultInstance());
    }

    public {{ PrefixName }} update(String id, String displayName) {
        return stub.update{{ PrefixName }}(Update{{ PrefixName }}Request.newBuilder().setId(id).setDisplayName(displayName).build());
    }

    public void delete(String id) {
        stub.delete{{ PrefixName }}(Delete{{ PrefixName }}Request.newBuilder().setId(id).build());
    }
}
