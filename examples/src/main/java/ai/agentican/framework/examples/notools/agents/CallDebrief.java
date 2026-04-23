/**
 * Domain: Customer Success
 * Tools: None
 */
package ai.agentican.framework.examples.notools.agents;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class CallDebrief {

    static String TASK_NAME = "Debrief Fintech Renewal Call";
    static String AGENT_NAME = "Customer Success Manager";
    static String SKILL_NAME = "Account health signals";
    static String INSTRUCTIONS = """
                            Summarize this customer call with {{param.customer}}. Distinguish what
                            the customer asked for from what the team committed to. Flag sentiment
                            cues and any unresolved topics.

                            Transcript:
                            {{param.transcript}}
                            """;

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var summarizer = agentican.agentTask(TASK_NAME)
                    .agent(AGENT_NAME)
                    .input(Call.class)
                    .output(CallSummary.class)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .build();

            var summary = summarizer.runAsync(call()).join();

            print(summary);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(CallDebrief.class.getResource("/call-debrief.yaml")).toURI());
    }

    static Call call() {

        return new Call("Acme Fintech", """
                [Rep] Thanks for the time today. How's the rollout going?
                [Customer] Honestly, slower than we hoped. The ingestion latency is still
                  spiking every time payroll runs.
                [Rep] Got it. I'll check with engineering on whether our auto-scaling
                  settings cover that pattern.
                [Customer] Also — the renewal is in 60 days and our CFO is asking about
                  ROI metrics. Can you send something by next week?
                [Rep] Absolutely, I'll have our CSM put together a value summary by
                  Tuesday.
                [Customer] Last thing — any update on the SSO feature we asked about in Q1?
                [Rep] Let me look into it and get back to you.
                """);
    }

    static void print(CallSummary s) {

        System.out.println("Sentiment: " + s.sentiment());
        System.out.println("\nCustomer asks:");    s.asks().forEach(a -> System.out.println("  • " + a));
        System.out.println("\nCommitments:");      s.commitments().forEach(c -> System.out.println("  → " + c));
        System.out.println("\nRisks / deferrals:"); s.risks().forEach(r -> System.out.println("  ⚠ " + r));
    }

    record Call(String customer, String transcript) {}

    record CallSummary(

            @JsonPropertyDescription("Overall customer sentiment — positive, neutral, frustrated, at-risk")
            String sentiment,

            @JsonPropertyDescription("Concrete requests or requirements from the customer")
            List<String> asks,

            @JsonPropertyDescription("Specific deliverables with timelines the team committed to — exclude hedged language")
            List<String> commitments,

            @JsonPropertyDescription("Unresolved items, deferrals, and sentiment signals the rep should watch")
            List<String> risks) {
    }
}
