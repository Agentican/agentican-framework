package ai.agentican.temporal.activity;

import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.dto.ToolCallRequest;

import java.util.List;

public class ToolCallActivityImpl implements ToolCallActivity {

    private final ToolkitResolver resolver;

    public ToolCallActivityImpl(ToolkitResolver resolver) {

        if (resolver == null) throw new IllegalArgumentException("resolver is required");

        this.resolver = resolver;
    }

    public ToolCallActivityImpl(List<Toolkit> toolkits) {

        if (toolkits == null) throw new IllegalArgumentException("toolkits is required");

        var copy = List.copyOf(toolkits);

        this.resolver = name -> copy.stream().filter(t -> t.handles(name)).findFirst().orElse(null);
    }

    @Override
    public String execute(ToolCallRequest request) {

        var toolkit = resolver.resolveFor(request.toolName());

        if (toolkit == null)
            throw new IllegalArgumentException("No toolkit handles tool: " + request.toolName());

        try {

            return toolkit.execute(request.toolName(), request.arguments());
        }
        catch (RuntimeException e) {

            throw e;
        }
        catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface ToolkitResolver {

        Toolkit resolveFor(String toolName);
    }
}
