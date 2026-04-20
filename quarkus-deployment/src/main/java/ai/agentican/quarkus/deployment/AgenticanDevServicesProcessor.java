package ai.agentican.quarkus.deployment;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;

import org.jboss.logging.Logger;

class AgenticanDevServicesProcessor {

    private static final Logger LOG = Logger.getLogger(AgenticanDevServicesProcessor.class);

    @BuildStep(onlyIf = IsDevelopment.class)
    void logDevModeInfo() {

        LOG.info("AgenticanRuntime Dev UI available at /q/dev-ui (look for the AgenticanRuntime card)");
    }
}
