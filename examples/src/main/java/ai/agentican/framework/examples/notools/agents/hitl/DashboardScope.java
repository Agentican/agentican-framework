package ai.agentican.framework.examples.notools.agents.hitl;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class DashboardScope {

    static String TASK_NAME = "Scope Payments Team Dashboard";
    static String AGENT_NAME = "Analytics Engineer";
    static String SKILL_NAME = "Dashboard scoping";
    static String INSTRUCTIONS = """
                            Scope a dashboard for this request. Apply the
                            dashboard-scoping skill strictly — every metric must map
                            to a named decision. If the request doesn't state the
                            decisions, ask before designing.

                            Request:
                            {{input}}
                            """;

    static void main() throws Exception {

        var builder = Agentican.builder()
                .configuration().yaml().path(config()).end()
                .registry().yaml().path(config()).end()
                .hitlManager(new HitlManager(new CliHitlNotifier()));

        try (var agentican = builder.build()) {

            var scope = agentican.task(TASK_NAME)
                    .agent(AGENT_NAME)
                    .skills(SKILL_NAME)
                    .instructions(INSTRUCTIONS)
                    .input(DashboardRequest.class)
                    .output(DashboardSpec.class)
                    .build();

            var spec = scope.start(request()).future().join();

            print(spec);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(DashboardScope.class.getResource("/dashboard-scope.yaml")).toURI());
    }

    static DashboardRequest request() {

        return new DashboardRequest(
                "Payments Platform",
                "Transaction volume, success rate and latency across our three "
                        + "payment processors",
                "Rolling 90 days");
    }

    static void print(DashboardSpec s) {

        System.out.println("Title: " + s.title() + "\n");
        System.out.println("Metrics:");
        s.metrics().forEach(m -> System.out.println("  • " + m));
        System.out.println("\nDimensions:");
        s.dimensions().forEach(d -> System.out.println("  • " + d));
        System.out.println("\nRefresh cadence: " + s.refreshCadence());
    }

    record DashboardRequest(

            @JsonPropertyDescription("Team requesting the dashboard")
            String requestingTeam,

            @JsonPropertyDescription("Data domain the dashboard covers")
            String dataDomain,

            @JsonPropertyDescription("Time window the dashboard spans")
            String timeframe) {
    }

    record DashboardSpec(

            @JsonPropertyDescription("Dashboard title — names the decision it supports")
            String title,

            @JsonPropertyDescription("Metrics, each mapped to the decision it drives")
            List<String> metrics,

            @JsonPropertyDescription("Dimensions for slicing the metrics (processor, region, merchant tier, etc.)")
            List<String> dimensions,

            @JsonPropertyDescription("Expected refresh cadence (real-time, hourly, daily, weekly)")
            String refreshCadence) {
    }
}
