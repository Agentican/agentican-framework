package ai.agentican.quarkus.deployment;

import ai.agentican.quarkus.devui.AgenticanDevUIService;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

class AgenticanDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem devUICard() {

        var card = new CardPageBuildItem();

        card.addPage(Page.webComponentPageBuilder()
                .title("Agents")
                .icon("font-awesome-solid:robot")
                .componentLink("qwc-agentican-agents.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Tasks")
                .icon("font-awesome-solid:list-check")
                .componentLink("qwc-agentican-tasks.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Checkpoints")
                .icon("font-awesome-solid:circle-pause")
                .componentLink("qwc-agentican-checkpoints.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Knowledge")
                .icon("font-awesome-solid:book")
                .componentLink("qwc-agentican-knowledge.js"));

        return card;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem jsonRPCProviders() {

        return new JsonRPCProvidersBuildItem(AgenticanDevUIService.class);
    }
}
