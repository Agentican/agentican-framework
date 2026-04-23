/**
 * Domain: HR / Recruiting
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ResumeReview {

    static String TASK_NAME = "Critique Backend Resume";
    static String AGENT_NAME = "Technical Recruiter";
    static String SKILL_NAME = "Resume review rubric";
    static String INSTRUCTIONS = """
                            Critique this resume for the role of {{param.role}}. Identify concrete
                            strengths that appear in the text, then list specific, actionable
                            improvements — each one tied to a line or claim in the resume.

                            Resume:
                            {{param.resume_text}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var critic = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(Submission.class)
                    .output(Critique.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var critique = critic.runAsync(input()).join();

            print(critique);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ResumeReview.class.getResource("/resume-review.yaml")).toURI());
    }

    static Submission input() {

        return new Submission("Senior Backend Engineer", """
                ALICE CHEN — Senior Software Engineer

                EXPERIENCE
                Stripe (2022-present) — Payments platform team
                  - Worked on payment processing infrastructure
                  - Contributed to Kafka-based event pipeline
                  - Mentored junior engineers

                Square (2018-2022) — Backend engineer
                  - Built REST APIs for merchant dashboard
                  - Improved test coverage
                  - Participated in on-call rotation

                SKILLS
                Java, Kafka, Kubernetes, Postgres, Redis, Git, Agile, SCRUM
                """);
    }

    static void print(Critique c) {

        System.out.println(c.overall() + "\n");
        System.out.println("Strengths:");  c.strengths().forEach(s -> System.out.println("  + " + s));
        System.out.println("\nImprovements:");
        c.improvements().forEach(i -> System.out.println("  • " + i.area() + ": " + i.suggestion()));
    }

    record Submission(String role, String resumeText) {}

    record Improvement(

            @JsonPropertyDescription("Area needing work — e.g. 'Quantified impact', 'System design depth', 'Buzzword density'")
            String area,

            @JsonPropertyDescription("Specific actionable suggestion grounded in a line from the resume")
            String suggestion) {
    }

    record Critique(

            @JsonPropertyDescription("One-sentence overall verdict — advance, strengthen, or pass")
            String overall,

            @JsonPropertyDescription("Concrete strengths grounded in actual resume content — not generic praise")
            List<String> strengths,

            @JsonPropertyDescription("Specific areas to improve, each tied to a line or claim in the resume")
            List<Improvement> improvements) {
    }
}
