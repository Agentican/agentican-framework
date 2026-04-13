package ai.agentican.framework.tools;

import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.hitl.AskQuestionToolkit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    private Agent dummyAgent(String name) {

        AgentRunner runner = (agent, task, activeSkills, toolkits, taskId, stepId, stepName) -> null;

        return new Agent(name, "Role for " + name, List.of(), runner);
    }

    @Test
    void agentRegistryRegisterAndGet() {

        var registry = new AgentRegistry();

        var agent = dummyAgent("agent-1");

        registry.register(agent);

        assertSame(agent, registry.get("agent-1"));
    }

    @Test
    void agentRegistryHasAndAll() {

        var registry = new AgentRegistry();

        registry.register(dummyAgent("agent-1"));
        registry.register(dummyAgent("agent-2"));

        assertTrue(registry.isRegistered("agent-1"));
        assertFalse(registry.isRegistered("unknown"));
        assertEquals(2, registry.asMap().size());
    }

    @Test
    void toolkitRegistryScopeForStep() {

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("tool-a", "Tool A", Map.of()),
                new ToolDefinition("tool-b", "Tool B", Map.of())));

        var registry = new ToolkitRegistry();

        registry.register("notion", toolkit);

        var scoped = registry.scopeForStep(List.of("notion"));

        assertEquals(2, scoped.size());
        assertTrue(scoped.containsKey("tool-a"));
        assertTrue(scoped.containsKey("tool-b"));
        assertSame(toolkit, scoped.get("tool-a"));
        assertSame(toolkit, scoped.get("tool-b"));
    }

    @Test
    void toolkitRegistryToolDefinitions() {

        var toolkitA = new MockToolkit(List.of(
                new ToolDefinition("tool-1", "First tool", Map.of())));

        var toolkitB = new MockToolkit(List.of(
                new ToolDefinition("tool-2", "Second tool", Map.of()),
                new ToolDefinition("tool-3", "Third tool", Map.of())));

        var registry = new ToolkitRegistry();

        registry.register("toolkit-a", toolkitA);
        registry.register("toolkit-b", toolkitB);

        var defs = registry.toolDefinitions(List.of("toolkit-a", "toolkit-b"));

        assertEquals(3, defs.size());
        assertEquals("tool-1", defs.get(0).name());
        assertEquals("tool-2", defs.get(1).name());
        assertEquals("tool-3", defs.get(2).name());
    }

    @Test
    void askQuestionExecuteThrowsUnsupported() {

        var toolkit = new AskQuestionToolkit();

        assertThrows(UnsupportedOperationException.class,
                () -> toolkit.execute("ASK_QUESTION", Map.of("question", "test")));
    }

    @Test
    void askQuestionToolHasQuestionHitlType() {

        var toolkit = new AskQuestionToolkit();

        assertEquals(HitlType.QUESTION, toolkit.hitlType("ASK_QUESTION"));
    }

    @Test
    void toolkitRegistryCloseCallsAutoCloseable() {

        var closed = new AtomicBoolean(false);

        var closeable = new CloseableToolkit(closed);

        var registry = new ToolkitRegistry();

        registry.register("test", closeable);
        registry.close();

        assertTrue(closed.get());
    }

    private static class CloseableToolkit implements Toolkit, AutoCloseable {

        private final AtomicBoolean closed;

        private final Tool tool = new ToolRecord("close-tool", "closeable", Map.of());

        CloseableToolkit(AtomicBoolean closed) { this.closed = closed; }

        @Override public List<Tool> tools() { return List.of(tool); }
        @Override public boolean handles(String toolName) { return "close-tool".equals(toolName); }
        @Override public String execute(String toolName, Map<String, Object> arguments) { return "ok"; }
        @Override public void close() { closed.set(true); }
    }
}
