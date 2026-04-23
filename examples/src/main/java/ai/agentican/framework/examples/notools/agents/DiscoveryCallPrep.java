/**
 * Domain: Sales
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class DiscoveryCallPrep {

    static String TASK_NAME = "Prep Fintech Discovery Call";
    static String AGENT_NAME = "Sales Development Representative";
    static String SKILL_NAME = "BANT framework";
    static String INSTRUCTIONS = """
                            Select {{param.count}} discovery questions calibrated to this prospect.
                            Skip questions that could be answered by desk research. For each
                            question, state the goal and a follow-up if the answer is vague.

                            Prospect: {{param.role}} at {{param.company}}
                            Known challenges: {{param.known_challenges}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var coach = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(Prospect.class)
                    .output(QuestionSet.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var set = coach.runAsync(prospect()).join();

            print(set);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DiscoveryCallPrep.class.getResource("/discovery-call-prep.yaml")).toURI());
    }

    static Prospect prospect() {

        return new Prospect(
                "Acme Fintech",
                "VP Engineering",
                "Building in-house AI-agent plumbing. Mentioned pain around audit trails and "
                        + "reproducibility when agents run unsupervised. Evaluating platforms after "
                        + "a failed hackathon prototype leaked customer PII.",
                5);
    }

    static void print(QuestionSet set) {

        set.questions().forEach(q -> {
            System.out.println("Q: " + q.question());
            System.out.println("  goal: " + q.goal());
            System.out.println("  follow-up: " + q.followUp() + "\n");
        });
    }

    record Prospect(String company, String role, String knownChallenges, int count) {}

    record DiscoveryQuestion(

            @JsonPropertyDescription("The question to ask — specific to this prospect, not a generic qualifier")
            String question,

            @JsonPropertyDescription("What the rep learns from the answer — the decision or pain it surfaces")
            String goal,

            @JsonPropertyDescription("Follow-up to use if the answer is vague or surface-level")
            String followUp) {
    }

    record QuestionSet(List<DiscoveryQuestion> questions) {}
}
