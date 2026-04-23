/**
 * Domain: Sales
 * Tools: HubSpot, Gmail (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class LeadQualification {

    static String TASK_NAME = "Qualify Fintech Prospect";
    static String PLAN_NAME = "Lead Qualification Pipeline";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var qualification = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(Lead.class)
                    .output(LeadAssessment.class)
                    .build();

            var assessment = qualification.runAsync(lead()).join();

            print(assessment);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(LeadQualification.class.getResource("/lead-qualification.yaml")).toURI());
    }

    static Lead lead() {

        return new Lead("Acme Fintech", "Jane Chen, VP Engineering");
    }

    static void print(LeadAssessment assessment) {

        System.out.println("Score: " + assessment.score() + " → " + assessment.recommendation());
        System.out.println("  BANT: " + assessment.bant());
        System.out.println("  " + assessment.rationale());
    }

    record Lead(String company, String contact) {}

    record Bant(

            @JsonPropertyDescription("Budget fit score 1-10")
            int budget,

            @JsonPropertyDescription("Authority fit score 1-10 — can the contact sign or directly influence the decision?")
            int authority,

            @JsonPropertyDescription("Need fit score 1-10 — does the pain point align with the product?")
            int need,

            @JsonPropertyDescription("Timeline fit score 1-10 — active evaluation within 6 months scores high")
            int timeline) {
    }

    record LeadAssessment(

            @JsonPropertyDescription("Composite score 1-10 across all BANT dimensions")
            int score,

            @JsonPropertyDescription("Per-dimension BANT breakdown")
            Bant bant,

            @JsonPropertyDescription("One of: advance, nurture, disqualify (advance ≥7, nurture 4-6, disqualify ≤3)")
            String recommendation,

            @JsonPropertyDescription("One-sentence rationale a sales manager needs to agree or disagree with the recommendation")
            String rationale) {
    }
}
