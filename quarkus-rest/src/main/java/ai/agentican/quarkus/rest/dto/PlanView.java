package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.Plan;

public record PlanView(String planId, String name, String description, Plan plan) {

    public static PlanView of(Plan plan) {

        return new PlanView(plan.id(), plan.name(), plan.description(), plan);
    }
}
