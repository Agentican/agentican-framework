package ai.agentican.framework;

import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.execution.WorkflowStepResult;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.util.Json;

final class WorkflowOutputMapper {

    private WorkflowOutputMapper() {}

    static String resolveOutputStep(WorkflowDefinition definition, Class<?> outputType) {

        if (outputType == null || outputType == Void.class) return null;

        if (outputType == WorkflowRunResult.class) return null;

        if (definition.outputStep() != null) return definition.outputStep();

        if (definition.steps().size() == 1) return definition.steps().getFirst().name();

        throw new IllegalStateException(
                "WorkflowDefinition '" + definition.name() + "' has " + definition.steps().size()
                        + " steps but no outputStep declared. Set WorkflowDefinition.builder(...).outputStep(name) "
                        + "to designate the step whose output is the definition's output.");
    }

    @SuppressWarnings("unchecked")
    static <R> R readTypedOutput(WorkflowRunResult result, String outputStep, Class<R> outputType) {

        if (outputType == WorkflowRunResult.class) return (R) result;

        if (result.status() != WorkflowRunStatus.COMPLETED)
            throw new WorkflowFailedException(result);

        if (outputType == null || outputType == Void.class) return null;

        var raw = result.stepResults().stream()
                .filter(s -> outputStep.equals(s.name()))
                .findFirst()
                .map(WorkflowStepResult::output)
                .orElse(null);

        if (raw == null || raw.isBlank())
            throw new WorkflowOutputException(raw, outputType,
                    new IllegalStateException("Output step '" + outputStep + "' produced no output"));

        if (outputType == String.class) return (R) raw;

        try {

            return Json.mapper().readValue(Json.unwrapMarkdownFences(raw), outputType);
        }
        catch (Exception e) {

            var stopReason = lastStopReason(result, outputStep);

            var hint = stopReason == StopReason.MAX_TOKENS
                    ? " [stopReason=MAX_TOKENS — LLM hit maxTokens limit; increase llm.maxTokens in config]"
                    : stopReason != null ? " [stopReason=" + stopReason + "]" : "";

            throw new WorkflowOutputException(raw, outputType, new RuntimeException(e.getMessage() + hint, e));
        }
    }

    private static StopReason lastStopReason(WorkflowRunResult result, String outputStep) {

        return result.stepResults().stream()
                .filter(s -> outputStep.equals(s.name()))
                .findFirst()
                .map(WorkflowStepResult::agentResults)
                .filter(ars -> !ars.isEmpty())
                .map(ars -> ars.getLast().run())
                .filter(run -> !run.turns().isEmpty())
                .map(run -> run.turns().getLast().response().stopReason())
                .orElse(null);
    }
}
