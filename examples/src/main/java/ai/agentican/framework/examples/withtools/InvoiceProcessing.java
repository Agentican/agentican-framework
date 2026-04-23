/**
 * Domain: Finance
 * Tools: Gmail, Slack (all via Composio)
 */
package ai.agentican.framework.examples.withtools;

import ai.agentican.framework.Agentican;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.nio.file.Path;
import java.util.Objects;

public class InvoiceProcessing {

    static String TASK_NAME = "Process Vendor Invoice";
    static String PLAN_NAME = "Invoice Processing";

    static void main() throws Exception {

        try (var agentican = Agentican.builder(config()).build()) {

            var processing = agentican.workflowTask(TASK_NAME)
                    .plan(PLAN_NAME)
                    .input(InvoiceRequest.class)
                    .output(ValidationResult.class)
                    .build();

            var result = processing.runAsync(request()).join();

            print(result);
        }
    }

    static Path config() throws Exception {

        return Path.of(Objects.requireNonNull(InvoiceProcessing.class.getResource("/invoice-processing.yaml")).toURI());
    }

    static InvoiceRequest request() {

        return new InvoiceRequest("msg-abc-123");
    }

    static void print(ValidationResult result) {

        System.out.println(result.vendor() + " — $" + result.amount() + " [" + result.decision() + "]");
        System.out.println(result.reason());
    }

    record InvoiceRequest(String emailId) {}

    record ValidationResult(

            @JsonPropertyDescription("Vendor name as it appears on the invoice")
            String vendor,

            @JsonPropertyDescription("Invoice total normalized to USD")
            double amount,

            @JsonPropertyDescription("One of: auto, needs-approval, reject per the AP policy rules")
            String decision,

            @JsonPropertyDescription("One-sentence explanation of the decision referencing the specific AP policy criterion")
            String reason) {
    }
}
