package ai.agentican.framework.invoker;

import ai.agentican.framework.orchestration.execution.TaskResult;

public class TaskFailedException extends RuntimeException {

    private final TaskResult taskResult;

    public TaskFailedException(TaskResult taskResult) {

        super("Task " + (taskResult != null ? taskResult.name() : "<unknown>")
                + " did not complete: status=" + (taskResult != null ? taskResult.status() : "<unknown>"));
        this.taskResult = taskResult;
    }

    public TaskResult taskResult() { return taskResult; }
}
