package ai.agentican.framework.registry;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

public interface WorkflowRegistry extends Catalog<WorkflowDefinition> {

    default void seed() { }
}
