package ai.agentican.framework;

import ai.agentican.framework.tools.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockToolkit implements Toolkit {

    private final List<Tool> tools;
    private final Map<String, String> responses = new LinkedHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> invocations = new java.util.concurrent.ConcurrentHashMap<>();
    private String defaultResponse = "{\"successful\": true}";

    public MockToolkit(List<ToolDefinition> toolDefs) {

        this.tools = new java.util.ArrayList<>(toolDefs.stream()
                .map(d -> (Tool) new ToolRecord(d.name(), d.description(), d.properties(), d.required()))
                .toList());
    }

    public MockToolkit withHitl(String toolName) {

        return withHitl(toolName, HitlType.APPROVAL);
    }

    public MockToolkit withHitl(String toolName, HitlType type) {

        for (int i = 0; i < tools.size(); i++) {

            var t = tools.get(i);

            if (t.name().equals(toolName))
                tools.set(i, new ToolRecord(t.name(), t.description(), t.properties(), t.required(), type));
        }

        return this;
    }

    public MockToolkit onExecute(String toolName, String response) {

        responses.put(toolName, response);
        return this;
    }

    public MockToolkit defaultResponse(String response) {

        this.defaultResponse = response;
        return this;
    }

    @Override
    public List<Tool> tools() {

        return tools;
    }

    @Override
    public boolean handles(String toolName) {

        return tools.stream().anyMatch(t -> t.name().equals(toolName));
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        invocations.computeIfAbsent(toolName, k -> new java.util.concurrent.atomic.AtomicInteger())
                .incrementAndGet();
        return responses.getOrDefault(toolName, defaultResponse);
    }

    public int invocationCount(String toolName) {

        var counter = invocations.get(toolName);
        return counter != null ? counter.get() : 0;
    }
}
