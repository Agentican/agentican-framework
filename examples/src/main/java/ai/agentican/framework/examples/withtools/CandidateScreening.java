/**
 * Domain: HR / Recruiting
 * Tools: Gmail (via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class CandidateScreening {

    static String TASK_NAME = "Shortlist Backend Engineer Hires";
    static String PLAN_NAME = "Candidate Screening Pipeline";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var screening = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(CandidateRoster.class)
                    .output(OutreachDrafts.class)
                    .build();

            var outreach = screening.runAsync(candidates()).join();

            print(outreach);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(CandidateScreening.class.getResource("/candidate-screening.yaml")).toURI());
    }

    static CandidateRoster candidates() {

        return new CandidateRoster("Senior Backend Engineer", List.of(
                new Candidate("Alice Chen", 7, List.of("Java"), "Kafka contributor, ex-Stripe"),
                new Candidate("Bob Park", 3, List.of("Python", "Go"), "ML background, startup"),
                new Candidate("Carol Davis", 10, List.of("Java"), "Distributed systems at AWS"),
                new Candidate("Dave Kim", 2, List.of("React"), "Bootcamp grad, frontend focus"),
                new Candidate("Eve Santos", 6, List.of("Kotlin", "Java"), "K8s expert, OSS maintainer")));
    }

    static void print(OutreachDrafts outreach) {

        outreach.drafts().forEach(draft ->
                System.out.println(draft.candidate() + "\n  " + draft.subject() + "\n  " + draft.body() + "\n"));
    }

    record Candidate(String name, int yearsExperience, List<String> skills, String background) {}
    record CandidateRoster(String role, List<Candidate> candidates) {}
    record OutreachDrafts(List<OutreachDraft> drafts) {}

    record OutreachDraft(

            @JsonPropertyDescription("Candidate's full name, matching the name that appeared in the ranking")
            String candidate,

            @JsonPropertyDescription("Personalized subject line, under 80 characters, no generic hook")
            String subject,

            @JsonPropertyDescription("Full email body, under 200 words, first-person, references specific background details")
            String body) {
    }
}
