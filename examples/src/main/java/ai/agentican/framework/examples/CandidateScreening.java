/**
 * ★★★★☆ — Loop step with per-item processing and HITL review.
 *
 * Domain: HR / Recruiting
 * Tools: Gmail (via Composio)
 * Features: Loop step (per-candidate), HITL review, skills, Gmail integration
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class CandidateScreening {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("candidate-screening.yaml");

var plan = PlanConfig.builder()
                .name("Candidate Screening Pipeline")
                .externalId("candidate-screening")
                .param("candidates", "JSON array of candidate summaries", null, true)
                .param("role", "Job title", null, true)
                .loop("screen", l -> l
                        .over("candidates")
                        .step("evaluate", s -> s
                                .agent("Talent Screener")
                                .skills("Hiring criteria")
                                .instructions("Screen this candidate for {{param.role}}:\n {{item}}")))
                .step("rank", s -> s
                        .agent("Talent Screener")
                        .instructions("Rank all candidates and recommend the top 3:\n {{step.screen.output}}")
                        .dependencies("screen")
                        .hitl())
                .step("outreach", s -> s
                        .agent("Recruiter")
                        .tools("gmail_create_email_draft")
                        .instructions("Draft outreach emails for approved candidates:\n {{step.rank.output}}")
                        .dependencies("rank"))
                .build();

        var builder = Agentican.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);

        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {

            var candidates = """
                    ["Alice Chen — 7yr Java, Kafka contributor, ex-Stripe",\
                     "Bob Park — 3yr Python/Go, ML background, startup",\
                     "Carol Davis — 10yr Java, distributed systems at AWS",\
                     "Dave Kim — 2yr bootcamp grad, React focus",\
                     "Eve Santos — 6yr Kotlin/Java, K8s expert, OSS maintainer"]""";

            var task = agentican.run(plan.toPlan(), Map.of(
                    "candidates", candidates,
                    "role", "Senior Backend Engineer"));

            System.out.println("Status: " + task.result().status());
        }
    }
}
