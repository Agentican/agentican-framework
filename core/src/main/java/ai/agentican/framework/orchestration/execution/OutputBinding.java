package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.llm.StructuredOutput;

public record OutputBinding(String stepName, StructuredOutput structuredOutput) {

    public OutputBinding {

        if (stepName == null || stepName.isBlank())
            throw new IllegalArgumentException("name is required");

        if (structuredOutput == null)
            throw new IllegalArgumentException("structuredOutput is required");
    }
}
