/**
 * ★☆☆☆☆ — Zero configuration. The Planner does everything.
 *
 * Domain: Any
 * Tools: None required (Planner creates agents and skills from scratch)
 * Features: PlannerAgent, agent/skill synthesis
 *
 * The simplest possible Agentican usage. No agents, skills, plans or tools registered.
 * The built-in Planner reads the task description, invents the agents and skills it needs,
 * builds a plan, and executes it.
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.RuntimeConfig;

public class QuickTask {

    public static void main(String[] args) {

        var config = RuntimeConfig.builder()
                .llm(LlmConfig.builder()
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .build())
                .build();

        try (var agentican = Agentican.builder().config(config).build()) {

            var task = agentican.run(
                    "Compare the pros and cons of event sourcing vs. traditional CRUD " +
                    "for a fintech startup processing 50K transactions per day. " +
                    "Consider developer experience, auditability, and operational cost.");

            System.out.println(task.result().output());
        }
    }
}
