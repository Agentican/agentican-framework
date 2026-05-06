package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class BrandTaglines {

    static String TASK_NAME = "Draft Platform Taglines";
    static String AGENT_NAME = "Brand Strategist";
    static String SKILL_NAME = "Brand voice";
    static String INSTRUCTIONS = """
                            Generate {{param.count}} taglines for {{param.product}} targeting
                            {{param.audience}}. The differentiator to lead with is: {{param.differentiator}}.
                            Vary the tonal register — don't return five rewordings of the same idea.
                            """;

    static void main() throws Exception {

        var builder = Agentican.builder()
                .configuration().yaml().path(engine()).end()
                .registry().yaml().path(config()).end();

        try (var agentican = builder.build()) {

            var generator = agentican.task(TASK_NAME)
                    .agent(AGENT_NAME)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .input(Brief.class)
                    .output(TaglineSet.class)
                    .build();

            var set = generator.start(brief()).future().join();

            print(set);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(BrandTaglines.class.getResource("/brand-taglines.yaml")).toURI());
    }


    static Path engine() throws Exception {

        return Path.of(Objects.requireNonNull(BrandTaglines.class.getResource("/engine.yaml")).toURI());
    }
    static Brief brief() {

        return new Brief(
                "Agentican",
                "engineering leaders evaluating agent frameworks",
                "plans are data, not prompts — auditable, testable, reusable",
                5);
    }

    static void print(TaglineSet set) {

        set.taglines().forEach(t ->
                System.out.println("[" + t.tone() + "] " + t.text() + "\n  why: " + t.rationale() + "\n"));
    }

    record Brief(String product, String audience, String differentiator, int count) {}

    record Tagline(

            @JsonPropertyDescription("Tonal register — e.g. confident, playful, precise, technical")
            String tone,

            @JsonPropertyDescription("Tagline text — under 12 words, names a concrete benefit or differentiator")
            String text,

            @JsonPropertyDescription("One-sentence explanation of why this lands for the stated audience — no marketing-copy speak")
            String rationale) {
    }

    record TaglineSet(List<Tagline> taglines) {}
}
