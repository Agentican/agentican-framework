package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.hitl.HitlCheckpoint;

public interface TaskListener {

    default void onPlanStarted(String taskId) {}
    default void onPlanCompleted(String taskId, String planId) {}
    default void onTaskStarted(String taskId) {}
    default void onTaskCompleted(String taskId, TaskStatus status) {}
    default void onStepStarted(String taskId, String stepId) {}
    default void onStepCompleted(String taskId, String stepId) {}
    default void onRunStarted(String taskId, String runId) {}
    default void onRunCompleted(String taskId, String runId, AgentStatus status) {}
    default void onTurnStarted(String taskId, String turnId) {}
    default void onTurnCompleted(String taskId, String turnId) {}
    default void onMessageSent(String taskId, String turnId) {}
    default void onResponseReceived(String taskId, String turnId, StopReason stopReason) {}
    default void onToolCallStarted(String taskId, String toolCallId) {}
    default void onToolCallCompleted(String taskId, String toolCallId) {}
    default void onHitlNotified(String taskId, String hitlId, HitlCheckpoint.Type type) {}
    default void onHitlResponded(String taskId, String hitlId, boolean approved) {}
    default void onToken(String taskId, String turnId, String token) {}
    default void onTaskReaped(String taskId,
                              ai.agentican.framework.orchestration.execution.resume.ReapReason reason) {}
    default void onTaskResumed(String taskId) {}
    default void onStepResumed(String taskId, String stepId) {}
    default void onRunResumed(String taskId, String runId) {}
    default void onTurnResumed(String taskId, String turnId,
                               ai.agentican.framework.orchestration.execution.resume.TurnResumeState state) {}
}
