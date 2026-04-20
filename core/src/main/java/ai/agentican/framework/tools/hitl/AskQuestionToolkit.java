package ai.agentican.framework.tools.hitl;

import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;

import java.util.List;
import java.util.Map;

public class AskQuestionToolkit implements Toolkit {

    public static final String TOOL_NAME = "ASK_QUESTION";

    private static final Tool ASK_USER_TOOL = new ToolRecord(TOOL_NAME,
            "Ask the user a question and wait for their response. Use this when you need "
                    + "clarification, a decision, or information that only the user can provide. "
                    + "The task will pause until the user responds. Use sparingly — only when you "
                    + "genuinely cannot proceed without user input.",
            Map.of("question", Map.of(
                            "type", "string",
                            "description", "The question to ask the user. Be specific and concise."),
                    "context", Map.of(
                            "type", "string",
                            "description", "Optional background explaining why you need this information.")),
            List.of("question"),
            HitlType.QUESTION);

    @Override
    public List<Tool> tools() {

        return List.of(ASK_USER_TOOL);
    }

    @Override
    public boolean handles(String toolName) {

        return TOOL_NAME.equals(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        throw new UnsupportedOperationException(
                "ASK_QUESTION is handled via QUESTION checkpoint, not direct execution");
    }
}
