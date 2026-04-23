/**
 * Domain: Marketing
 * Tools: None
 * HITL: Question via CLI — the brief intentionally omits the target audience,
 *       so the agent uses ASK_QUESTION to find out before writing copy.
 */
package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class LandingCopy {

    static String TASK_NAME = "Write Product Landing Hero";
    static String AGENT_NAME = "Brand Writer";
    static String SKILL_NAME = "Hero copy rules";
    static String INSTRUCTIONS = """
                            Write hero copy (headline, subheadline, primary CTA) for
                            this landing page. Apply the hero-copy-rules skill strictly —
                            if anything you need is missing from the brief, ask before
                            writing.

                            Brief:
                            {{input}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var writer = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(LandingPageBrief.class)
                    .output(HeroCopy.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var copy = writer.runAsync(brief()).join();

            print(copy);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(LandingCopy.class.getResource("/landing-copy.yaml")).toURI());
    }

    static LandingPageBrief brief() {

        return new LandingPageBrief(
                "Driftwave — event-ingestion platform with streaming-first schema registry.",
                "product homepage hero (above the fold)");
    }

    static void print(HeroCopy h) {

        System.out.println("Headline:    " + h.headline());
        System.out.println("Subheadline: " + h.subheadline());
        System.out.println("CTA:         " + h.primaryCta());
    }

    record LandingPageBrief(

            @JsonPropertyDescription("One-sentence product description")
            String product,

            @JsonPropertyDescription("The specific page surface the copy appears on")
            String surface) {
    }

    record HeroCopy(

            @JsonPropertyDescription("Headline — 8 words or fewer, calibrated to the audience register")
            String headline,

            @JsonPropertyDescription("Subheadline — one sentence naming the concrete outcome the reader gets")
            String subheadline,

            @JsonPropertyDescription("Primary call to action — verbatim button label, calibrated to the audience")
            String primaryCta) {
    }
}
