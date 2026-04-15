package ai.agentican.framework.orchestration.planning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReuseExisting(
        String planRef,
        Map<String, String> inputs) implements PlannerDecision {

    public ReuseExisting {

        if (inputs == null)
            inputs = Map.of();
    }
}
