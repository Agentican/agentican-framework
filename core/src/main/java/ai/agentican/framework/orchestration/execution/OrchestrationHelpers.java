package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Placeholders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrchestrationHelpers {

    private OrchestrationHelpers() { }

    public record Dependencies(
            Map<String, Set<String>> forward,
            Map<String, Set<String>> dependents,
            Map<String, WorkflowStep> stepsByName) { }

    public static Dependencies computeDependencies(WorkflowDefinition plan) {

        var paramNames = plan.params().stream().map(WorkflowParam::name).collect(Collectors.toSet());

        return computeDependencies(plan.steps(), paramNames);
    }

    public static Dependencies computeDependencies(List<WorkflowStep> steps, Set<String> externalNames) {

        var forward     = new HashMap<String, Set<String>>();
        var dependents  = new HashMap<String, Set<String>>();
        var stepsByName = new HashMap<String, WorkflowStep>();

        for (var step : steps) {

            var name = step.name();

            stepsByName.put(name, step);

            var depSet = new LinkedHashSet<>(step.dependencies());

            if (step instanceof WorkflowStepAgent agent) {

                var matcher = Placeholders.STEP_OUTPUT_PATTERN.matcher(agent.instructions());

                while (matcher.find()) depSet.add(matcher.group(1));
            }

            if (step instanceof WorkflowStepLoop loop && !externalNames.contains(loop.over()))
                depSet.add(loop.over());

            if (step instanceof WorkflowStepBranch branch && !externalNames.contains(branch.from()))
                depSet.add(branch.from());

            forward.put(name, Set.copyOf(depSet));

            for (var dep : depSet)
                dependents.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(name);
        }

        var immutableDependents = new HashMap<String, Set<String>>();

        dependents.forEach((k, v) -> immutableDependents.put(k, Set.copyOf(v)));

        return new Dependencies(Map.copyOf(forward), Map.copyOf(immutableDependents), Map.copyOf(stepsByName));
    }

    public static List<List<WorkflowStep>> computeLayers(WorkflowDefinition plan) {

        var paramNames = plan.params().stream().map(WorkflowParam::name).collect(Collectors.toSet());

        return computeLayers(plan.steps(), paramNames, plan.name());
    }

    public static List<List<WorkflowStep>> computeLayers(List<WorkflowStep> steps,
                                                         Set<String> externalNames,
                                                         String planLabel) {

        var deps = computeDependencies(steps, externalNames);

        var done = new HashSet<String>();
        var layers = new ArrayList<List<WorkflowStep>>();

        while (done.size() < steps.size()) {

            var layer = new ArrayList<WorkflowStep>();

            for (var step : steps) {

                if (done.contains(step.name())) continue;

                var stepDeps = deps.forward().get(step.name());

                var ready = stepDeps.stream().allMatch(d -> done.contains(d) || !deps.forward().containsKey(d));

                if (ready) layer.add(step);
            }

            if (layer.isEmpty())
                throw new IllegalArgumentException(
                        "Plan '" + planLabel + "' has a dependency cycle or references an unknown step");

            for (var step : layer) done.add(step.name());

            layers.add(List.copyOf(layer));
        }

        return List.copyOf(layers);
    }

    public static String serializeOutput(Object output, Class<?> outputType) {

        if (outputType == Void.class || output == null)
            return "";

        if (output instanceof String s)
            return s;

        try {

            return Json.mapper().writeValueAsString(output);
        }
        catch (Exception e) {

            throw new RuntimeException("Failed to serialize code step output: " + e.getMessage(), e);
        }
    }

    public static Object resolveInput(Object planInput, Class<?> inputType,
                                      Map<String, String> params, Map<String, String> stepOutputs) {

        if (inputType == Void.class || planInput == null)
            return null;

        var rawNode = planInput instanceof JsonNode n ? n : Json.mapper().valueToTree(planInput);

        var resolvedNode = resolvePlaceholdersInTree(rawNode, params, stepOutputs);

        if (JsonNode.class.isAssignableFrom(inputType))
            return resolvedNode;

        if (Map.class.isAssignableFrom(inputType))
            return Json.mapper().convertValue(resolvedNode, Map.class);

        return Json.mapper().convertValue(resolvedNode, inputType);
    }

    private static JsonNode resolvePlaceholdersInTree(JsonNode node, Map<String, String> params,
                                                      Map<String, String> stepOutputs) {

        if (node == null || node.isNull()) return node;

        if (node instanceof ObjectNode obj) {

            var resolved = Json.mapper().createObjectNode();

            obj.fields().forEachRemaining(entry ->
                    resolved.set(entry.getKey(), resolvePlaceholdersInTree(entry.getValue(), params, stepOutputs)));

            return resolved;
        }

        if (node instanceof ArrayNode arr) {

            var resolved = Json.mapper().createArrayNode();

            arr.forEach(child -> resolved.add(resolvePlaceholdersInTree(child, params, stepOutputs)));

            return resolved;
        }

        if (node instanceof TextNode text) {

            var raw         = text.asText();
            var afterParams = Placeholders.resolveParams(raw, params);
            var afterSteps  = Placeholders.resolveStepOutputsRaw(afterParams, stepOutputs);

            return raw.equals(afterSteps) ? text : new TextNode(afterSteps);
        }

        return node;
    }

    public static WorkflowStepBranch.Branch selectBranch(WorkflowStepBranch step, String upstreamOutput) {

        var trimmed = upstreamOutput == null ? "" : upstreamOutput.strip().toLowerCase();

        for (var branch : step.branches())
            if (trimmed.equals(branch.name().toLowerCase()))
                return branch;

        for (var branch : step.branches())
            if (trimmed.contains(branch.name().toLowerCase()))
                return branch;

        if (upstreamOutput != null) {

            try {

                int start = upstreamOutput.indexOf('[');
                int end   = upstreamOutput.lastIndexOf(']');

                if (start >= 0 && end > start) {

                    var jsonPart = upstreamOutput.substring(start, end + 1);

                    List<String> parsed = Json.mapper().readValue(jsonPart, new TypeReference<>() {});

                    if (!parsed.isEmpty()) {

                        var first = parsed.getFirst().strip().toLowerCase();

                        for (var branch : step.branches())
                            if (first.equals(branch.name().toLowerCase()))
                                return branch;
                    }
                }
            }
            catch (Exception _) { }
        }

        if (step.defaultBranch() != null)

            for (var branch : step.branches())
                if (branch.name().equals(step.defaultBranch()))
                    return branch;

        return null;
    }
}
