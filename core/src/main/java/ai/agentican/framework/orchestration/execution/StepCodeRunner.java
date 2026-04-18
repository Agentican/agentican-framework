package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.StepContext;
import ai.agentican.framework.orchestration.model.PlanStepCode;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Placeholders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class StepCodeRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepCodeRunner.class);

    private final CodeStepRegistry codeStepRegistry;
    private final TaskStateStore taskStateStore;
    private final HitlManager hitlManager;

    StepCodeRunner(CodeStepRegistry codeStepRegistry,
                   TaskStateStore taskStateStore, HitlManager hitlManager) {

        this.codeStepRegistry = codeStepRegistry;
        this.taskStateStore = taskStateStore;
        this.hitlManager = hitlManager;
    }

    TaskStepResult run(PlanStepCode<?> taskStep, Map<String, String> parentStepOutputs,
                       Map<String, String> taskParams, AtomicBoolean cancelled,
                       String taskId, String stepId) {

        var slug = taskStep.codeSlug();
        var registered = codeStepRegistry.get(slug);

        if (registered == null) {

            var message = "No code step registered for slug '" + slug + "'";
            LOG.error(message);

            return new TaskStepResult(taskStep.name(), TaskStatus.FAILED, message, List.of());
        }

        LOG.info("Running code step '{}' (slug={})", taskStep.name(), slug);

        var spec = registered.spec();
        var inputType = spec.inputType();
        var outputType = spec.outputType();

        try {

            Object typedInput = resolveInput(taskStep.inputs(), inputType, parentStepOutputs, taskParams);

            var context = new StepContext(taskId, stepId, cancelled, taskStateStore, hitlManager);

            @SuppressWarnings({"unchecked", "rawtypes"})
            Object output = ((CodeStep) registered.executor()).execute(typedInput, context);

            var stored = serializeOutput(output, outputType);

            return new TaskStepResult(taskStep.name(), TaskStatus.COMPLETED, stored, List.of());

        } catch (RuntimeException e) {

            LOG.error("Code step '{}' (slug={}) threw: {}", taskStep.name(), slug, e.getMessage(), e);

            return new TaskStepResult(taskStep.name(), TaskStatus.FAILED,
                    "Error: " + e.getMessage(), List.of(), e);
        }
    }

    private Object resolveInput(Object planInputs, Class<?> inputType,
                                Map<String, String> parentStepOutputs,
                                Map<String, String> taskParams) {

        if (inputType == Void.class || planInputs == null)
            return null;

        var rawNode = planInputs instanceof JsonNode n ? n : Json.mapper().valueToTree(planInputs);
        var resolvedNode = resolvePlaceholders(rawNode, parentStepOutputs, taskParams);

        if (JsonNode.class.isAssignableFrom(inputType))
            return resolvedNode;

        if (Map.class.isAssignableFrom(inputType))
            return Json.mapper().convertValue(resolvedNode, Map.class);

        return Json.mapper().convertValue(resolvedNode, inputType);
    }

    private JsonNode resolvePlaceholders(JsonNode node, Map<String, String> parentStepOutputs,
                                          Map<String, String> taskParams) {

        if (node == null || node.isNull()) return node;

        if (node instanceof ObjectNode obj) {

            var resolved = Json.mapper().createObjectNode();
            obj.fields().forEachRemaining(entry ->
                    resolved.set(entry.getKey(),
                            resolvePlaceholders(entry.getValue(), parentStepOutputs, taskParams)));
            return resolved;
        }

        if (node instanceof ArrayNode arr) {

            var resolved = Json.mapper().createArrayNode();
            arr.forEach(child -> resolved.add(resolvePlaceholders(child, parentStepOutputs, taskParams)));
            return resolved;
        }

        if (node instanceof TextNode text) {

            var raw = text.asText();
            var afterParams = Placeholders.resolveParams(raw, taskParams);
            var afterSteps = Placeholders.resolveStepOutputsRaw(afterParams, parentStepOutputs);
            return raw.equals(afterSteps) ? text : new TextNode(afterSteps);
        }

        return node;
    }

    private String serializeOutput(Object output, Class<?> outputType) throws RuntimeException {

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
}
