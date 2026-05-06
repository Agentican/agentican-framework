package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class IncidentPostmortem {

    static String TASK_NAME = "Draft Ingestion Outage Postmortem";
    static String AGENT_NAME = "Incident Communications Lead";
    static String SKILL_NAME = "Postmortem writing";
    static String INSTRUCTIONS = """
                            Draft the customer-facing postmortem email for this incident.
                            Apply the postmortem-writing skill strictly — no softening
                            language, quantify impact, name the mechanism.

                            Incident:
                            {{input}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder()
        .registry().yaml().path(config()).end()

                .hitlManager(new HitlManager(new CliHitlNotifier()))
        .build()) {

            var draft = agentican.task(TASK_NAME)
                    .agent(AGENT_NAME)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .hitl(true)
                    .input(IncidentBrief.class)
                    .output(PostmortemEmail.class)
                    .build();

            var email = draft.start(incident()).future().join();

            print(email);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(IncidentPostmortem.class.getResource("/incident-postmortem.yaml")).toURI());
    }

    static IncidentBrief incident() {

        return new IncidentBrief(
                "Ingestion API",
                142,
                "About 38% of inbound ingestion requests returned 503 between 14:08 and "
                        + "16:30 UTC. Roughly 2.1M records were delayed. Seven enterprise "
                        + "customers escalated during the window; two had downstream incidents "
                        + "of their own.",
                "A deploy to the ingestion control plane rolled out a migration that took "
                        + "a table lock on the request-routing table during peak hours. "
                        + "Health checks passed because they read a cached routing entry.");
    }

    static void print(PostmortemEmail e) {

        System.out.println("Subject: " + e.subject() + "\n");
        System.out.println(e.body());
        System.out.println("\nRemediations:");
        e.remediations().forEach(r -> System.out.println("  • " + r));
    }

    record IncidentBrief(

            @JsonPropertyDescription("Service or product affected")
            String service,

            @JsonPropertyDescription("Incident duration in minutes")
            int durationMinutes,

            @JsonPropertyDescription("Customer-observable impact, quantified where possible")
            String customerImpact,

            @JsonPropertyDescription("Root cause narrative — the mechanism, not the person")
            String rootCause) {
    }

    record PostmortemEmail(

            @JsonPropertyDescription("Email subject — 8 words or fewer, no softening language")
            String subject,

            @JsonPropertyDescription("Email body — what, impact, cause, remediation preamble")
            String body,

            @JsonPropertyDescription("Specific remediations, each naming an owner and a date")
            List<String> remediations) {
    }
}
