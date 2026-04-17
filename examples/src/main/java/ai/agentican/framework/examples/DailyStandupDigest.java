/**
 * ★★☆☆☆ — Single agent, multiple tool integrations.
 *
 * Domain: Engineering
 * Tools: GitHub, Linear, Slack (via Composio)
 * Features: One agent with multiple tools, cross-service data gathering
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;

import java.util.Map;

public class DailyStandupDigest {

    public static void main(String[] args) {

        var defs = ExampleLoader.load("daily-standup-digest.yaml");

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
                .composio(ComposioConfig.of(System.getenv("COMPOSIO_API_KEY"), "user-1"))
                .build();

        var plan = PlanConfig.builder()
                .name("Daily Standup Digest")
                .externalId("daily-standup-digest")
                .param("team", "Team or repo name", null, true)
                .param("channel", "Slack channel to post to", null, true)
                .step("gather-and-post", s -> s
                        .agent("Engineering Assistant")
                        .tools("github_list_repo_activities",
                               "linear_search_issues",
                               "slack_send_message")
                        .instructions(
                                "For the {{param.team}} team:\n" +
                                "1. Check GitHub for yesterday's commits and open PRs\n" +
                                "2. Check Linear for ticket status changes\n" +
                                "3. Format a standup digest: Done / In Progress / Blocked\n" +
                                "4. Post it to {{param.channel}} on Slack"))
                .build();

        var builder = Agentican.builder().config(config).plan(plan);
        defs.agents().forEach(builder::agent);

        try (var agentican = builder.build()) {
            var task = agentican.run(plan.toPlan(), Map.of(
                    "team", "platform-team",
                    "channel", "#platform-standup"));
            System.out.println("Status: " + task.result().status());
        }
    }
}
