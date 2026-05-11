package ai.agentican.quarkus.rest.catalog;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.registry.WorkflowRegistry;

import java.util.ArrayList;
import java.util.List;

public final class CatalogReferences {

    private CatalogReferences() {}

    public static List<String> plansReferencingAgent(WorkflowRegistry plans, String agentRef) {

        var hits = new ArrayList<String>();

        for (var plan : plans.list()) {
            if (stepsReferenceAgent(plan.steps(), agentRef))
                hits.add(plan.name());
        }

        return hits;
    }

    public static List<String> plansReferencingSkill(WorkflowRegistry plans, String skillRef) {

        var hits = new ArrayList<String>();

        for (var plan : plans.list()) {
            if (stepsReferenceSkill(plan.steps(), skillRef))
                hits.add(plan.name());
        }

        return hits;
    }

    private static boolean stepsReferenceAgent(List<WorkflowStep> steps, String ref) {

        for (var step : steps) {

            switch (step) {
                case WorkflowStepAgent a -> {
                    if (ref.equals(a.agentName())) return true;
                }
                case WorkflowStepLoop l -> {
                    if (stepsReferenceAgent(l.body(), ref)) return true;
                }
                case WorkflowStepBranch b -> {
                    for (var path : b.branches())
                        if (stepsReferenceAgent(path.steps(), ref)) return true;
                }
                case WorkflowStepCode<?> c -> {}
            }
        }

        return false;
    }

    private static boolean stepsReferenceSkill(List<WorkflowStep> steps, String ref) {

        for (var step : steps) {

            switch (step) {
                case WorkflowStepAgent a -> {
                    if (a.skills() != null && a.skills().contains(ref)) return true;
                }
                case WorkflowStepLoop l -> {
                    if (stepsReferenceSkill(l.body(), ref)) return true;
                }
                case WorkflowStepBranch b -> {
                    for (var path : b.branches())
                        if (stepsReferenceSkill(path.steps(), ref)) return true;
                }
                case WorkflowStepCode<?> c -> {}
            }
        }

        return false;
    }
}
