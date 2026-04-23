/**
 * Domain: Marketing
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class MessageVariants {

    static String TASK_NAME = "Draft Product Launch Copy";
    static String PLAN_NAME = "Message Variants";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var generator = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(MessageBrief.class)
                    .output(VariantSet.class)
                    .build();

            var set = generator.runAsync(brief()).join();

            print(set);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(MessageVariants.class.getResource("/message-variants.yaml")).toURI());
    }

    static MessageBrief brief() {

        return new MessageBrief(
                "Our new deploy pipeline cuts time-to-production from 45 minutes to 4.",
                "engineering managers evaluating platform tooling",
                3);
    }

    static void print(VariantSet set) {

        set.variants().forEach(v -> System.out.println("[" + v.tone() + "] " + v.subject() + "\n  " + v.body() + "\n"));
    }

    record MessageBrief(String coreMessage, String audience, int variantCount) {}

    record Variant(

            @JsonPropertyDescription("Tonal register used — one of: professional, conversational, direct, technical")
            String tone,

            @JsonPropertyDescription("Subject line, under 60 characters, matches the tonal register")
            String subject,

            @JsonPropertyDescription("Body copy, 40-80 words, reads consistent with the register from start to finish")
            String body) {
    }

    record VariantSet(List<Variant> variants) {}
}
