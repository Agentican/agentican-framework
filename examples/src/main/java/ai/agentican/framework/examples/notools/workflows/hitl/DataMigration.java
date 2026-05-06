package ai.agentican.framework.examples.notools.workflows.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.examples.notools.agents.hitl.CliHitlNotifier;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class DataMigration {

    static String TASK_NAME = "WorkflowDefinition Postgres-To-Aurora Cutover";
    static String PLAN_NAME = "Data Migration WorkflowDefinition";

    static void main() throws Exception {

        var builder = Agentican.builder()
                .configuration().yaml().path(engine()).end()
                .registry().yaml().path(config()).end()
                .hitlManager(new HitlManager(new CliHitlNotifier()));

        try (var agentican = builder.build()) {

            var migration = agentican.workflow(PLAN_NAME)
                    .input(MigrationScenario.class)
                    .output(Runbook.class)
                    .build();

            var runbook = migration.start(scenario()).future().join();

            print(runbook);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DataMigration.class.getResource("/data-migration.yaml")).toURI());
    }


    static Path engine() throws Exception {

        return Path.of(Objects.requireNonNull(DataMigration.class.getResource("/engine.yaml")).toURI());
    }
    static MigrationScenario scenario() {

        return new MigrationScenario(
                "Self-managed Postgres 14 on EC2, primary + 2 replicas, 3.1 TB",
                "Aurora Postgres 16 multi-AZ in us-east-1",
                "Must stay available for read traffic throughout. Write downtime "
                        + "budget is 6 minutes on a scheduled Saturday 02:00 UTC window.");
    }

    static void print(Runbook r) {

        System.out.println("Summary: " + r.summary() + "\n");
        System.out.println("Runbook steps:");
        for (int i = 0; i < r.steps().size(); i++)
            System.out.println("  " + (i + 1) + ". " + r.steps().get(i));
    }

    record MigrationScenario(

            @JsonPropertyDescription("Description of the current system (version, topology, size)")
            String currentSystem,

            @JsonPropertyDescription("Description of the target system")
            String targetSystem,

            @JsonPropertyDescription("Timing and business constraints that shape the cutover")
            String constraints) {
    }

    record Runbook(

            @JsonPropertyDescription("One-sentence summary of the runbook's goal")
            String summary,

            @JsonPropertyDescription("Ordered operator steps, each with command + expected output + failure action + owner")
            List<String> steps) {
    }
}
