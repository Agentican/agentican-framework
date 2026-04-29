package ai.agentican.quarkus.rest.error;

import java.util.List;

public class CatalogConflictException extends RuntimeException {

    private final String code;
    private final List<String> referring;

    public CatalogConflictException(String code, String message) {

        this(code, message, List.of());
    }

    public CatalogConflictException(String code, String message, List<String> referring) {

        super(message);
        this.code = code;
        this.referring = List.copyOf(referring);
    }

    public String code() { return code; }

    public List<String> referring() { return referring; }
}
