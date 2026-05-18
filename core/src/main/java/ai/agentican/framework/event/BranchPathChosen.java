package ai.agentican.framework.event;

public record BranchPathChosen(String taskId, String stepId, String pathName) implements AgenticanEvent { }
