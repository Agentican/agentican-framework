package ai.agentican.framework.llm;

public class LlmCallException extends RuntimeException {

    private final String agentName;
    private final String stepName;
    private final int turn;
    private final int attemptsMade;

    public LlmCallException(String agentName, String stepName, int turn, int attemptsMade, Throwable cause) {

        super(buildMessage(agentName, stepName, turn, attemptsMade, cause), cause);

        this.agentName = agentName;
        this.stepName = stepName;
        this.turn = turn;
        this.attemptsMade = attemptsMade;
    }

    public String agentName() { return agentName; }
    public String stepName() { return stepName; }
    public int turn() { return turn; }
    public int attemptsMade() { return attemptsMade; }

    private static String buildMessage(String agentName, String stepName, int turn, int attemptsMade, Throwable cause) {

        var causeMsg = cause != null ? cause.getMessage() : "unknown error";

        return "LLM call failed: agent='%s' step='%s' turn=%d attempts=%d — %s"
                .formatted(agentName, stepName, turn, attemptsMade, causeMsg);
    }
}
