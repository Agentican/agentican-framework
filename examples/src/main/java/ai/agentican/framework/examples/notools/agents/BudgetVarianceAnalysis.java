/**
 * Domain: Finance
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class BudgetVarianceAnalysis {

    static String TASK_NAME = "Explain Q1 Cloud Variance";
    static String AGENT_NAME = "Budget Analyst";
    static String SKILL_NAME = "Variance analysis framework";
    static String INSTRUCTIONS = """
                            Explain the variance on line item {{param.line_item}} for period
                            {{param.period}}: planned {{param.planned}}, actual {{param.actual}}.
                            Identify likely drivers and recommend one next action.

                            Context: {{param.context}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var analyst = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(Variance.class)
                    .output(Explanation.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var expl = analyst.runAsync(variance()).join();

            print(expl);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(BudgetVarianceAnalysis.class.getResource("/budget-variance-analysis.yaml")).toURI());
    }

    static Variance variance() {

        return new Variance(
                "Cloud infrastructure",
                180_000.0,
                242_500.0,
                "Q1 2026",
                "Two new services shipped in February; engineering headcount grew 18% in Q1; "
                        + "recent AWS pricing announcement affected S3 and egress.");
    }

    static void print(Explanation e) {

        System.out.println(e.direction() + " variance of " + e.magnitude());
        System.out.println("\nLikely drivers:");
        e.likelyDrivers().forEach(d -> System.out.println("  • " + d.driver() + " (" + d.confidence() + ")"));
        System.out.println("\nNext action: " + e.nextAction());
    }

    record Variance(String lineItem, double planned, double actual, String period, String context) {}

    record Driver(

            @JsonPropertyDescription("Concrete driver of the variance — name the actual factor, not a tautology")
            String driver,

            @JsonPropertyDescription("Confidence in this driver — one of: high, medium, low, insufficient-context")
            String confidence) {
    }

    record Explanation(

            @JsonPropertyDescription("One of: favorable (under plan), unfavorable (over plan)")
            String direction,

            @JsonPropertyDescription("Magnitude as percent-of-plan — e.g. '+35% over plan'")
            String magnitude,

            @JsonPropertyDescription("Most likely drivers, ordered by confidence")
            List<Driver> likelyDrivers,

            @JsonPropertyDescription("One concrete next action the finance team should take")
            String nextAction) {
    }
}
