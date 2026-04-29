package ai.agentican.framework.vector.code;

public record RetrieveQuery(String vectorIndex, String query, int k) {

    public RetrieveQuery {

        if (vectorIndex == null || vectorIndex.isBlank())
            throw new IllegalArgumentException("vectorIndex is required");

        if (query == null) query = "";
        if (k <= 0)        k     = 5;
    }
}
