/**
 * ★★☆☆☆ — Single agent, single tool integration.
 *
 * Domain: Productivity
 * Tools: Google Calendar (via Composio)
 * Features: One agent step, Composio tool access, plan parameters
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.time.LocalDate;
import java.util.Map;

public class MeetingPrepBrief {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("meeting-prep-brief.yaml");

var plan = PlanConfig.builder()
                .name("Meeting Prep Brief")
                .externalId("meeting-prep-brief")
                .param("date", "Date to prepare briefs for", null, true)
                .step("prepare", s -> s
                        .agent("Executive Assistant")
                        .tools("googlecalendar_find_event")
                        .instructions("Pull my calendar for {{param.date}}. " +
                                      "For each meeting, prepare a brief with: " +
                                      "attendees, purpose, and 3 talking points."))
                .build();

        var builder = Agentican.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);
        defs.agents().forEach(builder::agent);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(),
                    Map.of("date", LocalDate.now().toString()));
            System.out.println(task.result().output());
        }
    }
}
