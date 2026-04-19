/**
 * ★★★☆☆ — Multi-agent pipeline with CRM tools and HITL approval.
 *
 * Domain: Sales
 * Tools: HubSpot, Gmail (via Composio)
 * Features: Multi-agent sequential plan, HITL approval before outreach, CRM integration
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class LeadQualification {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("lead-qualification.yaml");

var plan = PlanConfig.builder()
                .name("Lead Qualification Pipeline")
                .externalId("lead-qualification")
                .param("company", "Company name", null, true)
                .param("contact", "Contact name and title", null, true)
                .step("research", s -> s
                        .agent("Lead Researcher")
                        .tools("hubspot_search_contacts")
                        .instructions("Research {{param.company}} and {{param.contact}}. " +
                                      "Check HubSpot for existing records. Build a profile."))
                .step("qualify", s -> s
                        .agent("Lead Qualification Specialist")
                        .skills("Sales playbook")
                        .instructions("Score and qualify this lead:\n{{step.research.output}}")
                        .dependencies("research")
                        .hitl())
                .step("outreach", s -> s
                        .agent("Outreach Specialist")
                        .tools("gmail_send_email", "hubspot_create_contact_note")
                        .instructions("Draft and send outreach to {{param.contact}} at " +
                                      "{{param.company}}.\n\nResearch: {{step.research.output}}\n" +
                                      "Qualification: {{step.qualify.output}}\n\n" +
                                      "Log the outreach in HubSpot.")
                        .dependencies("qualify"))
                .build();

        var builder = Agentican.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);
        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of(
                    "company", "Acme Fintech",
                    "contact", "Jane Chen, VP Engineering"));
            System.out.println("Status: " + task.result().status());
        }
    }
}
