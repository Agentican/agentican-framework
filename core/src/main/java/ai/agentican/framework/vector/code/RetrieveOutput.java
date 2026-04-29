package ai.agentican.framework.vector.code;

import java.util.List;

public record RetrieveOutput(List<RetrieveHit> hits, String formatted) {

    public RetrieveOutput {

        if (hits == null) hits = List.of();
        if (formatted == null) formatted = "";
    }
}
