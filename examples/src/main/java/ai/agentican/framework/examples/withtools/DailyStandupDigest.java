/**
 * Domain: Engineering
 * Tools: GitHub, Linear, Slack (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class DailyStandupDigest {

    static String TASK_NAME = "Post Platform Team Standup";
    static String PLAN_NAME = "Daily Standup Digest";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var standup = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(StandupRequest.class)
                    .output(StandupDigest.class)
                    .build();

            var digest = standup.runAsync(request()).join();

            print(digest);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DailyStandupDigest.class.getResource("/daily-standup-digest.yaml")).toURI());
    }

    static StandupRequest request() {

        return new StandupRequest("platform-team", "#platform-standup");
    }

    static void print(StandupDigest digest) {

        System.out.println("Done:");        digest.done().forEach(s -> System.out.println("  " + s));
        System.out.println("In Progress:"); digest.inProgress().forEach(s -> System.out.println("  " + s));
        System.out.println("Blocked:");     digest.blocked().forEach(s -> System.out.println("  " + s));
    }

    record StandupRequest(String team, String channel) {}

    record StandupDigest(

            @JsonPropertyDescription("Work completed in the last 24 hours — one item per entry, prefixed by author")
            List<String> done,

            @JsonPropertyDescription("Work actively in flight — one item per entry, prefixed by author")
            List<String> inProgress,

            @JsonPropertyDescription("Work blocked — one item per entry, include the blocker reason")
            List<String> blocked) {
    }
}
