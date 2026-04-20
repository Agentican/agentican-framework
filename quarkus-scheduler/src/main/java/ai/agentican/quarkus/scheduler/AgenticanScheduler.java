package ai.agentican.quarkus.scheduler;

import ai.agentican.framework.AgenticanRuntime;

import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AgenticanScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanScheduler.class);

    @Inject
    AgenticanRuntime agentican;

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledTaskConfig config;

    @PostConstruct
    void registerScheduledTasks() {

        var tasks = config.scheduled();

        if (tasks.isEmpty()) return;

        for (var task : tasks) {

            if (!task.enabled()) {

                LOG.info("Scheduled task '{}' is disabled, skipping", task.name());

                continue;
            }

            LOG.info("Registering scheduled task '{}' with cron '{}'", task.name(), task.cron());

            scheduler.newJob(task.name())
                    .setCron(task.cron())
                    .setTask(executionContext -> {

                        LOG.info("Executing scheduled task '{}'", task.name());

                        var handle = agentican.run(task.description());

                        handle.resultAsync().whenComplete((result, error) -> {

                            if (error != null)
                                LOG.error("Scheduled task '{}' failed: {}", task.name(), error.getMessage());
                            else
                                LOG.info("Scheduled task '{}' completed: {}", task.name(), result.status());
                        });
                    })
                    .schedule();
        }

        LOG.info("Registered {} scheduled task(s)",
                tasks.stream().filter(ScheduledTaskConfig.ScheduledTask::enabled).count());
    }
}
