package ai.agentican.framework.invoker;

import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.execution.TaskStepResult;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.util.Json;

final class AgenticanOutputReader {

    static String resolveOutputStep(Plan plan, Class<?> outputType) {

        if (outputType == null || outputType == Void.class) return null;

        if (plan.outputStep() != null) return plan.outputStep();

        if (plan.steps().size() == 1) return plan.steps().getFirst().name();

        throw new IllegalStateException(
                "Plan '" + plan.name() + "' has " + plan.steps().size()
                        + " steps but no outputStep declared. Set Plan.builder(...).outputStep(stepName) "
                        + "to designate the step whose output is the plan's output.");
    }

    @SuppressWarnings("unchecked")
    static <R> R readTypedOutput(TaskResult result, String outputStep, Class<R> outputType) {

        if (result.status() != TaskStatus.COMPLETED)
            throw new TaskFailedException(result);

        if (outputType == null || outputType == Void.class) return null;

        var raw = result.stepResults().stream()
                .filter(s -> outputStep.equals(s.stepName()))
                .findFirst()
                .map(s -> s.output())
                .orElse(null);

        if (raw == null || raw.isBlank())
            throw new OutputParseException(raw, outputType,
                    new IllegalStateException("Output step '" + outputStep + "' produced no output"));

        if (outputType == String.class) return (R) raw;

        try {

            return Json.mapper().readValue(raw, outputType);
        }
        catch (Exception e) {

            var stopReason = lastStopReason(result, outputStep);
            var hint = stopReason == StopReason.MAX_TOKENS
                    ? " [stopReason=MAX_TOKENS — LLM hit maxTokens limit; increase llm.maxTokens in config]"
                    : stopReason != null ? " [stopReason=" + stopReason + "]" : "";
            throw new OutputParseException(raw, outputType,
                    new RuntimeException(e.getMessage() + hint, e));
        }
    }

    private static StopReason lastStopReason(TaskResult result, String outputStep) {

        return result.stepResults().stream()
                .filter(s -> outputStep.equals(s.stepName()))
                .findFirst()
                .map(TaskStepResult::agentResults)
                .filter(ars -> !ars.isEmpty())
                .map(ars -> ars.getLast().run())
                .filter(run -> !run.turns().isEmpty())
                .map(run -> run.turns().getLast().response().stopReason())
                .orElse(null);
    }

    private AgenticanOutputReader() {}
}
