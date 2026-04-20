package ai.agentican.quarkus;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;

import io.smallrye.mutiny.Uni;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReactiveHitlNotifierTest {

    @Test
    void toSyncBridgesUniToBlockingCall() {

        var seen = new AtomicReference<HitlCheckpoint>();

        ReactiveHitlNotifier reactive = (mgr, cp) -> Uni.createFrom().voidItem()
                .onItem().invoke(() -> seen.set(cp));

        var sync = ReactiveHitlNotifier.toSync(reactive);
        var cp = new HitlCheckpoint("cp1", HitlCheckpoint.Type.QUESTION, "step", "desc", "q?");

        sync.onCheckpoint(new HitlManager((m, c) -> {}), cp);

        assertNotNull(seen.get(), "reactive notifier should have been invoked");
        assertEquals("cp1", seen.get().id());
    }
}
