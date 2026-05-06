package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class PromotionPackage {

    static String TASK_NAME = "Draft Staff Engineer Promotion";
    static String AGENT_NAME = "Engineering Manager";
    static String SKILL_NAME = "Promotion rubric";
    static String INSTRUCTIONS = """
                            Draft the promotion packet for this engineer. Apply the
                            promotion-rubric skill strictly: every rationale claim must
                            name a specific project and the next-level behavior it
                            demonstrates, and comp numbers must stay in band.

                            Candidate:
                            {{input}}
                            """;

    static void main() throws Exception {

        var builder = Agentican.builder()
                .configuration().yaml().path(engine()).end()
                .registry().yaml().path(config()).end()
                .hitlManager(new HitlManager(new CliHitlNotifier()));

        try (var agentican = builder.build()) {

            var draft = agentican.task(TASK_NAME)
                    .agent(AGENT_NAME)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .hitl(true)
                    .input(Engineer.class)
                    .output(PromotionPlan.class)
                    .build();

            var plan = draft.start(engineer()).future().join();

            print(plan);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(PromotionPackage.class.getResource("/promotion-package.yaml")).toURI());
    }


    static Path engine() throws Exception {

        return Path.of(Objects.requireNonNull(PromotionPackage.class.getResource("/engine.yaml")).toURI());
    }
    static Engineer engineer() {

        return new Engineer(
                "Senior Engineer, Platform",
                "L5",
                2,
                "Led the migration of event ingestion from batch to streaming, "
                        + "bringing end-to-end latency from 40 minutes to under 90 "
                        + "seconds. Designed the schema-registry service that now "
                        + "serves 14 teams across three orgs. Mentored two mid-level "
                        + "engineers through their first architecture reviews. Wrote "
                        + "the team's on-call runbook from scratch after the 2025 Q4 "
                        + "outage; paging volume dropped 60% the following quarter.",
                245000.0);
    }

    static void print(PromotionPlan p) {

        System.out.println("New level:      " + (p.newLevel() == null ? "— (strengthen-and-re-nominate)" : p.newLevel()));
        System.out.println("Base increase:  " + p.baseIncreasePct() + "%");
        System.out.println("Equity refresh: $" + p.equityRefreshUsd());
        System.out.println("\nRationale:\n" + p.rationale());
        System.out.println("\nTeam announcement:\n" + p.teamAnnouncement());
    }

    record Engineer(

            @JsonPropertyDescription("Role title and team")
            String role,

            @JsonPropertyDescription("Current level tag (e.g., L3, L4, L5)")
            String currentLevel,

            @JsonPropertyDescription("Years at current level")
            int yearsAtLevel,

            @JsonPropertyDescription("Impact evidence — specific projects, outcomes, and next-level behaviors")
            String evidenceSummary,

            @JsonPropertyDescription("Current annual base salary in USD")
            double currentBaseUsd) {
    }

    record PromotionPlan(

            @JsonPropertyDescription("New level if promoted (e.g., L6); null if not promoted this cycle")
            String newLevel,

            @JsonPropertyDescription("Base salary increase percentage within rubric bands")
            int baseIncreasePct,

            @JsonPropertyDescription("Equity refresh grant value in USD")
            double equityRefreshUsd,

            @JsonPropertyDescription("Rationale where each claim names a specific project and the next-level behavior it demonstrates")
            String rationale,

            @JsonPropertyDescription("Team announcement — celebratory but names the new scope the engineer owns, not generic praise")
            String teamAnnouncement) {
    }
}
