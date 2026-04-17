/**
 * ★★★★★ — Full feature showcase: parallel, branch, HITL, knowledge, multiple tools.
 *
 * Domain: Customer Success
 * Tools: HubSpot, Gmail, Google Calendar, Slack (via Composio)
 * Features: Parallel steps, branch routing, HITL approval, knowledge extraction,
 *           skills, multiple tool integrations
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class CustomerOnboarding {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("customer-onboarding.yaml");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(ComposioConfig.of(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .build();

        var plan = PlanConfig.builder()
                .name("Customer Onboarding")
                .externalId("customer-onboarding")
                .param("customer_id", "Customer identifier", null, true)
                .param("company", "Company name", null, true)
                .param("contact_email", "Primary contact email", null, true)

                // Parallel due diligence
                .step("kyc", s -> s
                        .agent("Compliance Officer")
                        .skills("Compliance policy")
                        .tools("hubspot_search_contacts")
                        .instructions("Run KYC for {{param.company}} ({{param.customer_id}})"))
                .step("credit-check", s -> s
                        .agent("Credit Analyst")
                        .instructions("Evaluate creditworthiness of {{param.company}}"))
                .step("product-fit", s -> s
                        .agent("Account Executive")
                        .tools("hubspot_search_contacts")
                        .instructions("Recommend a product package for {{param.company}}"))

                // Risk scoring (waits for all parallel streams)
                .step("risk-score", s -> s
                        .agent("Risk Manager")
                        .skills("Risk framework")
                        .instructions("Score risk for onboarding {{param.company}}:\n" +
                                      "KYC: {{step.kyc.output}}\n" +
                                      "Credit: {{step.credit-check.output}}\n" +
                                      "Product fit: {{step.product-fit.output}}")
                        .dependencies("kyc", "credit-check", "product-fit"))

                // Branch on risk
                .branch("approval", b -> b
                        .from("risk-score")
                        .defaultPath("auto-approve")
                        .path("auto-approve", p -> p
                                .agent("Risk Manager")
                                .instructions("Low-risk customer. Approve onboarding."))
                        .path("manual-review", p -> p
                                .step("review", s -> s
                                        .agent("Risk Manager")
                                        .instructions("Present risk assessment for review:\n" +
                                                      "{{step.risk-score.output}}")
                                        .hitl())))

                // Welcome sequence (after approval)
                .step("welcome-email", s -> s
                        .agent("Onboarding Manager")
                        .tools("gmail_send_email")
                        .instructions("Send welcome email to {{param.contact_email}} with " +
                                      "product details from {{step.product-fit.output}}")
                        .dependencies("approval"))
                .step("schedule-kickoff", s -> s
                        .agent("Onboarding Manager")
                        .tools("googlecalendar_create_event")
                        .instructions("Schedule a 30-min kickoff call with " +
                                      "{{param.contact_email}}")
                        .dependencies("approval"))
                .step("notify-team", s -> s
                        .agent("Onboarding Manager")
                        .tools("slack_send_message")
                        .instructions("Post to #customer-success: new customer " +
                                      "{{param.company}} onboarded")
                        .dependencies("approval"))
                .build();

        var builder = Agentican.builder().config(config).plan(plan);
        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of(
                    "customer_id", "CUST-2026-0417",
                    "company", "Acme Fintech",
                    "contact_email", "jane.chen@acmefintech.com"));
            System.out.println("Status: " + task.result().status());

            // The Knowledge Expert extracted facts about Acme Fintech from each step:
            // KYC status, credit assessment, product recommendation, risk score.
            // Future plans involving this customer recall those facts automatically.
        }
    }
}
