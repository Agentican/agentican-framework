/**
 * Domain: Finance
 * Tools: None
 */
package ai.agentican.framework.examples.notools.workflows;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class ExpenseAudit {

    static String TASK_NAME = "Audit Employee Expenses";
    static String PLAN_NAME = "Expense Audit";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var audit = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(ExpenseReport.class)
                    .output(AuditSummary.class)
                    .build();

            var summary = audit.runAsync(report()).join();

            print(summary);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(ExpenseAudit.class.getResource("/expense-audit.yaml")).toURI());
    }

    static ExpenseReport report() {

        return new ExpenseReport("jane.chen@example.com", List.of(
                new Expense("Delta Airlines", 842.00, "travel", "SEA → SFO, booked 3 days prior"),
                new Expense("Marriott SF", 350.00, "lodging", "Two nights, downtown SF"),
                new Expense("Blue Bottle", 12.50, "meals", "Client coffee — A. Patel"),
                new Expense("Amazon", 600.00, "software", "Team license renewals"),
                new Expense("Uber Eats", 95.00, "meals", "Friday 11pm, solo")));
    }

    static void print(AuditSummary summary) {

        System.out.println("Flagged: " + summary.flaggedCount() + " / total findings: " + summary.findings().size());
        summary.topConcerns().forEach(c -> System.out.println("  ⚠ " + c));
        System.out.println("\nAll findings:");
        summary.findings().forEach(f -> System.out.println("  [" + f.decision() + "] " + f.vendor() + " $" + f.amount() + " — " + f.reason()));
    }

    record Expense(String vendor, double amount, String category, String note) {}

    record ExpenseReport(String employee, List<Expense> expenses) {}

    record ExpenseFinding(

            @JsonPropertyDescription("Vendor name from the expense")
            String vendor,

            @JsonPropertyDescription("Amount in USD from the expense")
            double amount,

            @JsonPropertyDescription("One of: compliant, needs-review, violation")
            String decision,

            @JsonPropertyDescription("One-sentence reason referencing the specific policy clause that drove the decision")
            String reason) {
    }

    record AuditSummary(

            @JsonPropertyDescription("Per-expense findings in the same order as the input")
            List<ExpenseFinding> findings,

            @JsonPropertyDescription("Count of findings with decision in {needs-review, violation}")
            int flaggedCount,

            @JsonPropertyDescription("Top 2-3 most significant issues with specific expense references")
            List<String> topConcerns) {
    }
}
