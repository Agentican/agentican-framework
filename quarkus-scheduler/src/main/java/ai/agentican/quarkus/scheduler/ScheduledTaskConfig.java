package ai.agentican.quarkus.scheduler;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

@ConfigMapping(prefix = "agentican")
public interface ScheduledTaskConfig {

    List<ScheduledTask> scheduled();

    interface ScheduledTask {

        String name();

        String cron();

        String description();

        @WithDefault("true")
        boolean enabled();
    }
}
