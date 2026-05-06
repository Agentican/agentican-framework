package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class SecurityAdvisory {

    static String TASK_NAME = "Draft Ingestion CVE Advisory";
    static String AGENT_NAME = "Security Engineer";
    static String SKILL_NAME = "Advisory writing";
    static String INSTRUCTIONS = """
                            Draft a customer-facing security advisory for this
                            vulnerability. Apply the advisory-writing skill strictly —
                            if any coordination prerequisite isn't confirmed in the
                            report, ask before drafting.

                            Report:
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
                    .input(VulnerabilityReport.class)
                    .output(AdvisoryDraft.class)
                    .build();

            var advisory = draft.start(report()).future().join();

            print(advisory);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(SecurityAdvisory.class.getResource("/security-advisory.yaml")).toURI());
    }


    static Path engine() throws Exception {

        return Path.of(Objects.requireNonNull(SecurityAdvisory.class.getResource("/engine.yaml")).toURI());
    }
    static VulnerabilityReport report() {

        return new VulnerabilityReport(
                "CVE-2026-11824",
                8.6,
                "Driftwave Ingestion API",
                "1.12.0 through 1.17.4",
                "Authentication bypass on the replay-debug endpoint allows an "
                        + "unauthenticated caller to trigger a replay of cached "
                        + "events for any customer bucket.");
    }

    static void print(AdvisoryDraft a) {

        System.out.println("Subject: " + a.subject() + "\n");
        System.out.println(a.body());
    }

    record VulnerabilityReport(

            @JsonPropertyDescription("CVE identifier assigned to this vulnerability")
            String cveId,

            @JsonPropertyDescription("CVSS v3.1 base score (0-10)")
            double cvssScore,

            @JsonPropertyDescription("Affected service or product name")
            String affectedService,

            @JsonPropertyDescription("Affected version range")
            String affectedVersions,

            @JsonPropertyDescription("One-paragraph description of the vulnerability mechanism, in plain language")
            String summary) {
    }

    record AdvisoryDraft(

            @JsonPropertyDescription("Advisory subject line — names the service and the severity tier")
            String subject,

            @JsonPropertyDescription("Advisory body — what, who's affected, what to do, timeline")
            String body) {
    }
}
