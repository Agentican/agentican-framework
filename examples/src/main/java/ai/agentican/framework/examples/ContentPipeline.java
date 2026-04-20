/**
 * ★★★☆☆ — Multi-agent sequential pipeline with skills and HITL quality gate.
 *
 * Domain: Marketing
 * Tools: Notion (via Composio) for publishing
 * Features: Multiple agents, skills, sequential dependencies, HITL step approval
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class ContentPipeline {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("content-pipeline.yaml");

var plan = PlanConfig.builder()
                .name("Content Pipeline")
                .externalId("content-pipeline")
                .param("topic", "Article topic", null, true)
                .param("audience", "Target audience", null, true)
                .step("research", s -> s
                        .agent("Content Researcher")
                        .instructions("Research {{param.topic}} for a {{param.audience}} audience. " +
                                      "Gather 5+ data points, expert quotes and recent developments."))
                .step("outline", s -> s
                        .agent("Content Writer")
                        .instructions("Create a detailed article outline from this research:\n" +
                                      "{{step.research.output}}")
                        .dependencies("research")
                        .hitl())
                .step("draft", s -> s
                        .agent("Content Writer")
                        .skills("SEO optimization")
                        .instructions("Write the full article from this outline:\n" +
                                      "{{step.outline.output}}")
                        .dependencies("outline"))
                .step("edit", s -> s
                        .agent("Copy Editor")
                        .skills("SEO optimization")
                        .instructions("Edit this article for publication:\n{{step.draft.output}}")
                        .dependencies("draft"))
                .step("publish", s -> s
                        .agent("Content Writer")
                        .tools("notion_create_page")
                        .instructions("Publish this article to the content database:\n" +
                                      "{{step.edit.output}}")
                        .dependencies("edit"))
                .build();

        var builder = AgenticanRuntime.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(new ComposioConfig(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .plan(plan);
        defs.agents().forEach(builder::agent);
        defs.skills().forEach(builder::skill);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of(
                    "topic", "how platform engineering reduces cognitive load",
                    "audience", "engineering managers"));
            System.out.println("Status: " + task.result().status());
        }
    }
}
