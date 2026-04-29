package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.SkillRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanValidator {

    private PlanValidator() {}

    public static List<String> validate(Plan plan, AgentRegistry agents, SkillRegistry skills) {

        var issues = new ArrayList<String>();

        if (plan == null) {
            issues.add("Plan is null");
            return issues;
        }

        var stepNames = collectStepNames(plan.steps(), new HashSet<>());

        if (plan.outputStep() != null && !stepNames.contains(plan.outputStep()))
            issues.add("outputStep '" + plan.outputStep() + "' is not a step in the plan");

        validateSteps(plan.steps(), stepNames, agents, skills, issues);

        var cycles = findCycles(plan.steps());
        cycles.forEach(c -> issues.add("Cycle in dependencies: " + c));

        return issues;
    }

    private static Set<String> collectStepNames(List<PlanStep> steps, Set<String> acc) {

        for (var step : steps) {
            acc.add(step.name());
            switch (step) {
                case PlanStepLoop l   -> collectStepNames(l.body(), acc);
                case PlanStepBranch b -> b.paths().forEach(p -> collectStepNames(p.body(), acc));
                case PlanStepAgent a  -> {}
                case PlanStepCode<?> c -> {}
            }
        }
        return acc;
    }

    private static void validateSteps(List<PlanStep> steps, Set<String> stepNames,
                                      AgentRegistry agents, SkillRegistry skills,
                                      List<String> issues) {

        for (var step : steps) {

            for (var dep : step.dependencies())
                if (!stepNames.contains(dep))
                    issues.add("Step '" + step.name() + "' depends on unknown step '" + dep + "'");

            switch (step) {
                case PlanStepAgent a -> {
                    if (agents != null && a.agentId() != null
                            && agents.get(a.agentId()) == null
                            && agents.getByName(a.agentId()) == null
                            && agents.getByExternalId(a.agentId()) == null)
                        issues.add("Step '" + a.name() + "' references unknown agent '" + a.agentId() + "'");

                    if (skills != null && a.skills() != null) {
                        for (var skill : a.skills()) {
                            if (skills.get(skill) == null
                                    && skills.getByName(skill) == null
                                    && skills.getByExternalId(skill) == null)
                                issues.add("Step '" + a.name() + "' references unknown skill '" + skill + "'");
                        }
                    }
                }
                case PlanStepLoop l -> {
                    if (l.over() != null && !stepNames.contains(l.over()))
                        issues.add("Loop step '" + l.name() + "' loops over unknown step '" + l.over() + "' "
                                + "(if this is a plan param, ignore)");
                    validateSteps(l.body(), stepNames, agents, skills, issues);
                }
                case PlanStepBranch b -> {
                    if (b.from() != null && !stepNames.contains(b.from()))
                        issues.add("Branch step '" + b.name() + "' branches from unknown step '" + b.from() + "' "
                                + "(if this is a plan param, ignore)");
                    b.paths().forEach(p -> validateSteps(p.body(), stepNames, agents, skills, issues));
                }
                case PlanStepCode<?> c -> {}
            }
        }
    }

    private static List<String> findCycles(List<PlanStep> steps) {

        var graph = new HashMap<String, List<String>>();
        for (var step : steps) graph.put(step.name(), List.copyOf(step.dependencies()));

        var cycles = new ArrayList<String>();
        var visited = new HashSet<String>();
        var onStack = new HashSet<String>();

        for (var start : graph.keySet())
            if (!visited.contains(start))
                dfs(start, graph, visited, onStack, new ArrayList<>(), cycles);

        return cycles;
    }

    private static void dfs(String node, Map<String, List<String>> graph, Set<String> visited,
                            Set<String> onStack, List<String> path, List<String> cycles) {

        visited.add(node);
        onStack.add(node);
        path.add(node);

        for (var dep : graph.getOrDefault(node, List.of())) {

            if (!graph.containsKey(dep)) continue;
            if (!visited.contains(dep))
                dfs(dep, graph, visited, onStack, path, cycles);
            else if (onStack.contains(dep)) {
                var cycleStart = path.indexOf(dep);
                cycles.add(String.join(" → ", path.subList(cycleStart, path.size())) + " → " + dep);
            }
        }

        onStack.remove(node);
        path.remove(path.size() - 1);
    }
}
