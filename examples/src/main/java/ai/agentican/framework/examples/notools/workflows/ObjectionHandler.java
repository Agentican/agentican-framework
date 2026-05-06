package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ObjectionHandler {

    static String TASK_NAME = "Handle Pricing Objection";
    static String PLAN_NAME = "Objection Handler";

    static void main() throws Exception {

        var builder = Agentican.builder()
                .configuration().yaml().path(config()).end()
                .registry().yaml().path(config()).end();

        try (var agentican = builder.build()) {

            var handler = agentican.workflow(PLAN_NAME)
                    .input(ObjectionContext.class)
                    .output(ResponseSet.class)
                    .build();

            var set = handler.start(input()).future().join();

            print(set);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ObjectionHandler.class.getResource("/objection-handler.yaml")).toURI());
    }

    static ObjectionContext input() {

        return new ObjectionContext(
                "Your platform is too expensive compared to our current setup.",
                "Agentican — agent orchestration framework",
                "VP Engineering at a 200-person fintech, evaluating 3 platforms, active project with a 30-day deadline");
    }

    static void print(ResponseSet set) {

        set.responses().forEach(r -> System.out.println("[" + r.strategy() + "] " + r.text() + "\n  next: " + r.nextStep() + "\n"));
    }

    record ObjectionContext(String objection, String product, String context) {}

    record SalesResponse(

            @JsonPropertyDescription("Playbook strategy used — one of: acknowledge-and-redirect, evidence-led, clarifying-question, cost-of-inaction")
            String strategy,

            @JsonPropertyDescription("Spoken-aloud response, under 60 words, does not start with \"but\"")
            String text,

            @JsonPropertyDescription("One concrete next step to propose after the response lands")
            String nextStep) {
    }

    record ResponseSet(List<SalesResponse> responses) {}
}
