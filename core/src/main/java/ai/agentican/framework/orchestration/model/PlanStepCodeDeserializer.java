package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.orchestration.code.CodeStepRegistry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry-aware deserializer for {@link PlanStepCode}. Looks up the
 * registered {@link ai.agentican.framework.orchestration.code.CodeStepSpec}
 * by {@code codeSlug} to discover the typed input class, then deserializes
 * the {@code inputs} field into that type.
 *
 * <p>The {@code CodeStepRegistry} must be supplied via Jackson's
 * {@code InjectableValues} (see
 * {@code ai.agentican.framework.orchestration.model.PlanCodec}). If no
 * registry is injected, the deserializer falls back to leaving the inputs
 * as a {@link JsonNode} — useful for read-only callers that only need to
 * inspect a plan's structure.
 */
public class PlanStepCodeDeserializer extends StdDeserializer<PlanStepCode<?>> {

    public PlanStepCodeDeserializer() {

        super(PlanStepCode.class);
    }

    @Override
    public PlanStepCode<?> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {

        var node = (JsonNode) parser.readValueAsTree();

        var nameNode = node.get("name");
        var slugNode = node.get("codeSlug");

        if (nameNode == null || slugNode == null)
            throw JsonMappingException.from(ctxt, "PlanStepCode requires 'name' and 'codeSlug' fields");

        var name = nameNode.asText();
        var slug = slugNode.asText();

        List<String> dependencies = new ArrayList<>();
        var depsNode = node.get("dependencies");

        if (depsNode != null && depsNode.isArray())
            for (var dep : depsNode)
                dependencies.add(dep.asText());

        var inputsNode = node.get("inputs");

        CodeStepRegistry registry;
        try {
            registry = (CodeStepRegistry) ctxt.findInjectableValue(
                    CodeStepRegistry.class.getName(), null, null);
        }
        catch (JsonMappingException e) {
            registry = null;
        }

        Object inputs;

        if (registry != null) {

            var registered = registry.get(slug);

            if (registered == null)
                throw JsonMappingException.from(ctxt, "Unknown code step slug: '" + slug + "'");

            var inputType = registered.spec().inputType();

            if (inputsNode == null || inputsNode.isNull() || inputType == Void.class)
                inputs = null;
            else
                inputs = ctxt.readTreeAsValue(inputsNode, inputType);
        }
        else {
            inputs = inputsNode != null && !inputsNode.isNull() ? inputsNode : null;
        }

        return new PlanStepCode<>(name, slug, inputs, dependencies);
    }
}
