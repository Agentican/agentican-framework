/**
 * Domain: Leadership / Planning
 * Tools: None
 * HITL: Question via CLI — the team context is provided but the company's
 *       strategic priorities for the quarter are omitted, so the agent asks
 *       before drafting objectives that would otherwise drift.
 */
package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class OkrDraft {

    static String TASK_NAME = "Draft Q2 Team OKRs";
    static String AGENT_NAME = "Engineering Director";
    static String SKILL_NAME = "OKR writing";
    static String INSTRUCTIONS = """
                            Draft quarterly OKRs for this team. Apply the okr-writing
                            skill strictly — every objective must roll up to a stated
                            company priority. If that direction isn't in the brief,
                            ask before writing.

                            Team:
                            {{input}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config())
                .hitlManager(new HitlManager(new CliHitlNotifier()))
                .build()) {

            var draft = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(TeamContext.class)
                    .output(QuarterlyOKRs.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var okrs = draft.runAsync(team()).join();

            print(okrs);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(OkrDraft.class.getResource("/okr-draft.yaml")).toURI());
    }

    static TeamContext team() {

        return new TeamContext(
                "Payments Platform",
                8,
                "Owns payment-orchestration across three processors, plus the "
                        + "merchant-facing webhook pipeline. Last quarter shipped a "
                        + "retry-with-idempotency layer that cut duplicate charges to "
                        + "effectively zero.");
    }

    static void print(QuarterlyOKRs o) {

        System.out.println("Objective:\n  " + o.objective());
        System.out.println("\nKey results:");
        o.keyResults().forEach(kr -> System.out.println("  • " + kr));
    }

    record TeamContext(

            @JsonPropertyDescription("Team name")
            String teamName,

            @JsonPropertyDescription("Number of engineers on the team")
            int headcount,

            @JsonPropertyDescription("Short summary of what the team owns and their recent wins")
            String domainSummary) {
    }

    record QuarterlyOKRs(

            @JsonPropertyDescription("One-sentence objective naming the outcome and the company priority it rolls up to")
            String objective,

            @JsonPropertyDescription("2-3 key results, each a single measurable number with a target and a baseline")
            List<String> keyResults) {
    }
}
