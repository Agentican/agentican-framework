package ai.agentican.quarkus.scheduler;

import ai.agentican.framework.Agentican;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AgenticanSchedulerTest {

    @Inject
    Agentican agentican;

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledTaskConfig config;

    @Test
    void schedulerModuleStartsCleanly() {

        assertNotNull(agentican, "Agentican should be available");
        assertNotNull(scheduler, "Scheduler should be available");
    }

    @Test
    void configParsesScheduledTasks() {

        var tasks = config.scheduled();

        assertNotNull(tasks);
        org.junit.jupiter.api.Assertions.assertFalse(tasks.isEmpty(),
                "Should have at least one scheduled task in config");
        org.junit.jupiter.api.Assertions.assertEquals("test-task", tasks.getFirst().name());
        org.junit.jupiter.api.Assertions.assertFalse(tasks.getFirst().enabled());
    }
}
