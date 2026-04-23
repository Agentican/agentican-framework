package ai.agentican.framework.invoker;

import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.execution.TaskStepResult;

public class TaskFailedException extends RuntimeException {

    private final TaskResult taskResult;

    public TaskFailedException(TaskResult taskResult) {

        super(buildMessage(taskResult), firstFailureCause(taskResult));

        this.taskResult = taskResult;
    }

    public TaskResult taskResult() { return taskResult; }

    private static String buildMessage(TaskResult result) {

        if (result == null) return "Task <unknown> did not complete";

        var sb = new StringBuilder("Task ").append(result.name())
                .append(" did not complete: status=").append(result.status());

        for (var step : result.stepResults()) {

            if (step.status() == TaskStatus.COMPLETED) continue;

            sb.append("\n  step '").append(step.stepName()).append("' ").append(step.status());

            if (step.output() != null && !step.output().isBlank())
                sb.append(": ").append(step.output());

            if (step.cause() != null)
                sb.append(" (").append(step.cause().getClass().getSimpleName())
                        .append(": ").append(step.cause().getMessage()).append(')');
        }

        return sb.toString();
    }

    private static Throwable firstFailureCause(TaskResult result) {

        if (result == null) return null;

        return result.stepResults().stream()
                .filter(s -> s.status() != TaskStatus.COMPLETED)
                .map(TaskStepResult::cause)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
