package ai.agentican.temporal.dto;

public record CodeInvocationRequest(
        String codeSlug,
        String taskId,
        String stepId,
        String stepName,
        Object input) {

}
