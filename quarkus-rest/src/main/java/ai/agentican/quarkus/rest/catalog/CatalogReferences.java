package ai.agentican.quarkus.rest.catalog;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepCode;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.framework.registry.PlanRegistry;

import java.util.ArrayList;
import java.util.List;

public final class CatalogReferences {

    private CatalogReferences() {}

    public static List<String> plansReferencingAgent(PlanRegistry plans, String agentRef) {

        var hits = new ArrayList<String>();

        for (var plan : plans.getAll()) {
            if (stepsReferenceAgent(plan.steps(), agentRef))
                hits.add(plan.name());
        }

        return hits;
    }

    public static List<String> plansReferencingSkill(PlanRegistry plans, String skillRef) {

        var hits = new ArrayList<String>();

        for (var plan : plans.getAll()) {
            if (stepsReferenceSkill(plan.steps(), skillRef))
                hits.add(plan.name());
        }

        return hits;
    }

    private static boolean stepsReferenceAgent(List<PlanStep> steps, String ref) {

        for (var step : steps) {

            switch (step) {
                case PlanStepAgent a -> {
                    if (ref.equals(a.agentId())) return true;
                }
                case PlanStepLoop l -> {
                    if (stepsReferenceAgent(l.body(), ref)) return true;
                }
                case PlanStepBranch b -> {
                    for (var path : b.paths())
                        if (stepsReferenceAgent(path.body(), ref)) return true;
                }
                case PlanStepCode<?> c -> {}
            }
        }

        return false;
    }

    private static boolean stepsReferenceSkill(List<PlanStep> steps, String ref) {

        for (var step : steps) {

            switch (step) {
                case PlanStepAgent a -> {
                    if (a.skills() != null && a.skills().contains(ref)) return true;
                }
                case PlanStepLoop l -> {
                    if (stepsReferenceSkill(l.body(), ref)) return true;
                }
                case PlanStepBranch b -> {
                    for (var path : b.paths())
                        if (stepsReferenceSkill(path.body(), ref)) return true;
                }
                case PlanStepCode<?> c -> {}
            }
        }

        return false;
    }
}
