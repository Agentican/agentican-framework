/**
 * ★★★★☆ — Branch with conditional HITL and payment tools.
 *
 * Domain: Finance
 * Tools: Gmail, Slack (via Composio)
 * Features: Three-path branch (auto/review/reject), conditional HITL, multiple tools
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class InvoiceProcessing {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("invoice-processing.yaml");

var plan = PlanConfig.builder()
                .name("Invoice Processing")
                .externalId("invoice-processing")
                .param("email_id", "Gmail message ID with the invoice", null, true)
                .step("extract", s -> s
                        .agent("Invoice Processor")
                        .tools("gmail_get_message")
                        .instructions("Extract invoice details from email {{param.email_id}}"))
                .step("validate", s -> s
                        .agent("Invoice Processor")
                        .skills("AP policy")
                        .instructions("Validate this invoice against AP policy:\n" +
                                      "{{step.extract.output}}")
                        .dependencies("extract"))
                .branch("route", b -> b
                        .from("validate")
                        .defaultPath("auto")
                        .path("auto", p -> p
                                .agent("Payment Agent")
                                .instructions("Process payment for this approved invoice:\n" +
                                              "{{step.extract.output}}"))
                        .path("needs-approval", p -> p
                                .step("review", s -> s
                                        .agent("Finance Controller")
                                        .instructions("Review for approval:\n" +
                                                      "{{step.extract.output}}\n" +
                                                      "Validation: {{step.validate.output}}")
                                        .hitl())
                                .step("process", s -> s
                                        .agent("Payment Agent")
                                        .instructions("Process approved payment:\n" +
                                                      "{{step.extract.output}}")))
                        .path("reject", p -> p
                                .agent("Invoice Processor")
                                .instructions("Draft a rejection notice to the vendor:\n" +
                                              "{{step.validate.output}}")))
                .step("notify", s -> s
                        .agent("Payment Agent")
                        .tools("slack_send_message")
                        .instructions("Post result to #finance-ops:\n{{step.route.output}}")
                        .dependencies("route"))
                .build();

        var builder = Agentican.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);
        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of("email_id", "msg-abc-123"));
            System.out.println("Status: " + task.result().status());
        }
    }
}
