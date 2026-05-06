package ai.agentican.framework;

import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.execution.WorkflowStepResult;

public class WorkflowFailedException extends RuntimeException {

    private final WorkflowRunResult taskResult;

    public WorkflowFailedException(WorkflowRunResult taskResult) {

        super(buildMessage(taskResult), firstFailureCause(taskResult));

        this.taskResult = taskResult;
    }

    public WorkflowRunResult taskResult() { return taskResult; }

    private static String buildMessage(WorkflowRunResult result) {

        if (result == null) return "Task <unknown> did not complete";

        var sb = new StringBuilder("Task ").append(result.name())
                .append(" did not complete: status=").append(result.status());

        for (var step : result.stepResults()) {

            if (step.status() == WorkflowRunStatus.COMPLETED) continue;

            sb.append("\n  step '").append(step.name()).append("' ").append(step.status());

            if (step.output() != null && !step.output().isBlank())
                sb.append(": ").append(step.output());

            if (step.cause() != null)
                sb.append(" (").append(step.cause().getClass().getSimpleName())
                        .append(": ").append(step.cause().getMessage()).append(')');
        }

        return sb.toString();
    }

    private static Throwable firstFailureCause(WorkflowRunResult result) {

        if (result == null) return null;

        return result.stepResults().stream()
                .filter(s -> s.status() != WorkflowRunStatus.COMPLETED)
                .map(WorkflowStepResult::cause)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
