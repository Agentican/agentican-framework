/**
 * Domain: Design / UX
 * Tools: None
 * HITL: Question on the intermediate `frame` step — the agent asks which
 *       product decision the synthesis is informing before framing the
 *       clusters, since the same clusters frame differently for different
 *       decisions.
 */
package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ResearchSynthesis {

    static String TASK_NAME = "Synthesize Onboarding Research";
    static String PLAN_NAME = "Research Synthesis";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var synth = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(ResearchCorpus.class)
                    .output(ResearchBrief.class)
                    .build();

            var brief = synth.runAsync(corpus()).join();

            print(brief);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ResearchSynthesis.class.getResource("/research-synthesis.yaml")).toURI());
    }

    static ResearchCorpus corpus() {

        return new ResearchCorpus(
                "Q2 onboarding usability study — new accounts, first 7 days",
                8,
                """
                        P1 (developer, small team): "I got stuck on the API-key
                        step for like ten minutes. I kept scrolling thinking the
                        key would appear at the bottom."
                        P2 (platform lead, midmarket): "The docs are great, the
                        setup wizard is confusing. Why are they different?"
                        P3 (developer, solo): "I just wanted to curl something.
                        I didn't want to install a CLI."
                        P4 (platform lead, enterprise): "Our security team won't
                        let us use the default config. We need to walk through
                        every env var before it can ship."
                        P5 (developer, small team): "The sample project was the
                        fastest part. That should be the first thing you see."
                        P6 (developer, solo): "I couldn't figure out how to test
                        without a production key. Got spooked and quit."
                        P7 (platform lead, midmarket): "We got blocked waiting
                        on a staging environment. Took three days."
                        P8 (developer, small team): "Support was great actually
                        — answered within an hour when I DM'd."
                        """);
    }

    static void print(ResearchBrief b) {

        System.out.println("Decision framing: " + b.decisionFraming() + "\n");
        System.out.println("Findings:");
        b.findings().forEach(f -> System.out.println("  • " + f));
    }

    record ResearchCorpus(

            @JsonPropertyDescription("Name of the research study")
            String studyName,

            @JsonPropertyDescription("Number of participants")
            int participantCount,

            @JsonPropertyDescription("Raw interview and usability-test notes")
            String rawNotes) {
    }

    record ResearchBrief(

            @JsonPropertyDescription("One-sentence statement of the decision this brief informs")
            String decisionFraming,

            @JsonPropertyDescription("Findings — each names a behavior, implication for the decision, and confidence")
            List<String> findings) {
    }
}
