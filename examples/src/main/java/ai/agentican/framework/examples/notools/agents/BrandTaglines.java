/**
 * Domain: Marketing
 * Tools: None
 */
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

        try (var agentican = Agentican.builder(config()).build()) {

            var generator = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(Brief.class)
                    .output(TaglineSet.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var set = generator.runAsync(brief()).join();

            print(set);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(BrandTaglines.class.getResource("/brand-taglines.yaml")).toURI());
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
