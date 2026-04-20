/**
 * ★★★★☆ — Branch routing with parallel notifications and tools.
 *
 * Domain: Operations / SRE
 * Tools: Slack, Linear, GitHub (via Composio)
 * Features: Branch step (severity routing), parallel steps in high-severity path
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class IncidentResponse {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("incident-response.yaml");

var plan = PlanConfig.builder()
                .name("Incident Response")
                .externalId("incident-response")
                .param("alert", "Alert message from monitoring", null, true)
                .param("service", "Affected service name", null, true)
                .step("triage", s -> s
                        .agent("Incident Triage Agent")
                        .skills("Incident runbook")
                        .instructions("Triage this alert for {{param.service}}: " +
                                      "{{param.alert}}"))
                .branch("respond", b -> b
                        .from("triage")
                        .defaultPath("low")
                        .path("low", p -> p
                                .agent("Incident Responder")
                                .instructions("Low-severity incident for {{param.service}}. " +
                                              "Create a ticket and notify #ops-alerts.\n" +
                                              "Triage: {{step.triage.output}}"))
                        .path("high", p -> p
                                .step("create-incident", s -> s
                                        .agent("Incident Responder")
                                        .tools("linear_create_issue")
                                        .instructions("Create P1/P2 incident ticket:\n" +
                                                      "{{step.triage.output}}"))
                                .step("page-oncall", s -> s
                                        .agent("Incident Responder")
                                        .tools("slack_send_message")
                                        .instructions("Page on-call in #incidents and " +
                                                      "create a war-room channel"))
                                .step("gather-context", s -> s
                                        .agent("Incident Triage Agent")
                                        .tools("github_search_code")
                                        .instructions("Search recent commits to " +
                                                      "{{param.service}} for causes"))))
                .build();

        var builder = AgenticanRuntime.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);
        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of(
                    "alert", "Error rate 15% on payment-service, p99 > 5s",
                    "service", "payment-service"));
            System.out.println("Status: " + task.result().status());
        }
    }
}
