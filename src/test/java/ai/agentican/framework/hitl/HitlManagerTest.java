package ai.agentican.framework.hitl;

import ai.agentican.framework.llm.ToolCall;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HitlManagerTest {

    @Test
    void createAndRespondApproval() {

        var manager = new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));

        var toolCall = new ToolCall("tc-1", "deploy", Map.of("env", "prod"));

        var checkpoint = manager.createToolApprovalCheckpoint(toolCall, "deploy-step");
        var response = manager.awaitResponse(checkpoint.id());

        assertTrue(response.approved());
    }

    @Test
    void createAndRespondRejection() {

        var manager = new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.reject("Bad idea")));

        var toolCall = new ToolCall("tc-2", "delete-all", Map.of());

        var checkpoint = manager.createToolApprovalCheckpoint(toolCall, "cleanup-step");
        var response = manager.awaitResponse(checkpoint.id());

        assertFalse(response.approved());
        assertEquals("Bad idea", response.feedback());
    }

    @Test
    void synchronousNotifierWorks() {

        var manager = new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve("Looks good")));

        var checkpoint = manager.createQuestionCheckpoint("Should we proceed?", null, "ask-step");
        var response = manager.awaitResponse(checkpoint.id());

        assertTrue(response.approved());
        assertEquals("Looks good", response.feedback());
    }

    @Test
    void timeoutReturnsRejection() {

        var manager = new HitlManager((mgr, cp) -> {}, Duration.ofMillis(100));

        var toolCall = new ToolCall("tc-3", "slow-tool", Map.of());
        var checkpoint = manager.createToolApprovalCheckpoint(toolCall, "slow-step");

        var start = System.currentTimeMillis();
        var response = manager.awaitResponse(checkpoint.id());
        var elapsed = System.currentTimeMillis() - start;

        assertFalse(response.approved());
        assertNotNull(response.feedback());
        assertTrue(response.feedback().contains("timed out"));
        assertTrue(elapsed >= 80, "Should have waited at least ~100ms, but was " + elapsed + "ms");
    }

    @Test
    void respondTwiceIgnored() {

        var manager = new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));

        var toolCall = new ToolCall("tc-4", "my-tool", Map.of());

        var checkpoint = manager.createToolApprovalCheckpoint(toolCall, "step");
        var response = manager.awaitResponse(checkpoint.id());

        assertTrue(response.approved());

        assertDoesNotThrow(() -> manager.respond(checkpoint.id(), HitlResponse.reject("too late")));
    }

    @Test
    void cancelCompletesWithRejection() {

        var manager = new HitlManager((mgr, cp) -> mgr.cancel(cp.id()));

        var toolCall = new ToolCall("tc-5", "cancel-me", Map.of());

        var checkpoint = manager.createToolApprovalCheckpoint(toolCall, "cancel-step");
        var response = manager.awaitResponse(checkpoint.id());

        assertFalse(response.approved());
        assertEquals("Cancelled", response.feedback());
    }
}
