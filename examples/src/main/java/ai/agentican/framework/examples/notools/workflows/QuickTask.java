package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import java.nio.file.Path;
import java.util.Objects;

public class QuickTask {

    static void main() throws Exception {

        try (var agentican = Agentican.builder()
        .registry().yaml().path(config()).end()
        .build()) {

            var result = agentican.run(task()).await();

            print(result);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(QuickTask.class.getResource("/quick-task.yaml")).toURI());
    }

    static String task() {

        return "Compare the pros and cons of event sourcing vs. traditional CRUD "
                + "for a fintech startup processing 50K transactions per day. "
                + "Consider developer experience, auditability, and operational cost.";
    }

    static void print(String result) {

        System.out.println(result);
    }
}
