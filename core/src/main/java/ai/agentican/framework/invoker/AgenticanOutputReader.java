package ai.agentican.framework.invoker;

import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.execution.TaskStatus;
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
            throw new OutputParseException(raw, outputType, e);
        }
    }

    private AgenticanOutputReader() {}
}
