package ai.agentican.quarkus;

import ai.agentican.framework.AgenticanService;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ResumeOnStartObserver {

    private static final Logger LOG = LoggerFactory.getLogger(ResumeOnStartObserver.class);

    @Inject
    AgenticanService agenticanService;

    @Inject
    AgenticanConfig config;

    void onStart(@Observes @Priority(100) StartupEvent event) {

        if (!config.resumeOnStart()) {
            LOG.info("Resume-on-start disabled via agentican.resume-on-start=false; skipping orphan reap");
            return;
        }

        try {
            agenticanService.resumeInterrupted(config.resumeMaxConcurrent());
        }
        catch (RuntimeException ex) {
            LOG.error("Resume-interrupted failed on startup: {}", ex.getMessage(), ex);
        }
    }
}
