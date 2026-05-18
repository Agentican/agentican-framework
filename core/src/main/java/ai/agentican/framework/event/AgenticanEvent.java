package ai.agentican.framework.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlanStarted.class,              name = "PlanStarted"),
        @JsonSubTypes.Type(value = PlanCompleted.class,            name = "PlanCompleted"),
        @JsonSubTypes.Type(value = TaskStarted.class,              name = "TaskStarted"),
        @JsonSubTypes.Type(value = TaskCompleted.class,            name = "TaskCompleted"),
        @JsonSubTypes.Type(value = TaskReaped.class,               name = "TaskReaped"),
        @JsonSubTypes.Type(value = TaskResumed.class,              name = "TaskResumed"),
        @JsonSubTypes.Type(value = StepStarted.class,              name = "StepStarted"),
        @JsonSubTypes.Type(value = StepCompleted.class,            name = "StepCompleted"),
        @JsonSubTypes.Type(value = StepTokenUsageAggregated.class, name = "StepTokenUsageAggregated"),
        @JsonSubTypes.Type(value = StepResumed.class,              name = "StepResumed"),
        @JsonSubTypes.Type(value = BranchPathChosen.class,         name = "BranchPathChosen"),
        @JsonSubTypes.Type(value = RunStarted.class,               name = "RunStarted"),
        @JsonSubTypes.Type(value = RunCompleted.class,             name = "RunCompleted"),
        @JsonSubTypes.Type(value = RunResumed.class,               name = "RunResumed"),
        @JsonSubTypes.Type(value = TurnStarted.class,              name = "TurnStarted"),
        @JsonSubTypes.Type(value = TurnCompleted.class,            name = "TurnCompleted"),
        @JsonSubTypes.Type(value = TurnAbandoned.class,            name = "TurnAbandoned"),
        @JsonSubTypes.Type(value = TurnResumed.class,              name = "TurnResumed"),
        @JsonSubTypes.Type(value = TokenStreamed.class,            name = "TokenStreamed"),
        @JsonSubTypes.Type(value = MessageSent.class,              name = "MessageSent"),
        @JsonSubTypes.Type(value = ResponseReceived.class,         name = "ResponseReceived"),
        @JsonSubTypes.Type(value = ToolCallStarted.class,          name = "ToolCallStarted"),
        @JsonSubTypes.Type(value = ToolCallCompleted.class,        name = "ToolCallCompleted"),
        @JsonSubTypes.Type(value = HitlNotified.class,             name = "HitlNotified"),
        @JsonSubTypes.Type(value = HitlResponded.class,            name = "HitlResponded")
})
public sealed interface AgenticanEvent
        permits PlanStarted, PlanCompleted,
                TaskStarted, TaskCompleted, TaskReaped, TaskResumed,
                StepStarted, StepCompleted, StepTokenUsageAggregated, StepResumed,
                BranchPathChosen,
                RunStarted, RunCompleted, RunResumed,
                TurnStarted, TurnCompleted, TurnAbandoned, TurnResumed, TokenStreamed,
                MessageSent, ResponseReceived,
                ToolCallStarted, ToolCallCompleted,
                HitlNotified, HitlResponded {

    String taskId();
}
