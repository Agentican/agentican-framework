package ai.agentican.framework.invoker;

public class OutputParseException extends RuntimeException {

    private final String rawOutput;
    private final Class<?> targetType;

    public OutputParseException(String rawOutput, Class<?> targetType, Throwable cause) {

        super(buildMessage(rawOutput, targetType, cause), cause);
        this.rawOutput = rawOutput;
        this.targetType = targetType;
    }

    public String rawOutput()      { return rawOutput; }
    public Class<?> targetType()   { return targetType; }

    private static String buildMessage(String rawOutput, Class<?> targetType, Throwable cause) {

        var snippet = rawOutput == null ? "<null>"
                : rawOutput.length() > 200 ? rawOutput.substring(0, 200) + "…(truncated)"
                : rawOutput;

        return "Failed to parse plan output as " + targetType.getName() + ": "
                + (cause != null ? cause.getMessage() : "unknown") + " — raw output: " + snippet;
    }
}
