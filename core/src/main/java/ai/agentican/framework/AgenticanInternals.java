package ai.agentican.framework;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.orchestration.execution.TaskRunner;
import ai.agentican.framework.store.TaskStateStore;
import ai.agentican.framework.orchestration.execution.TaskDecorator;
import ai.agentican.framework.orchestration.execution.TaskListener;

import java.util.concurrent.ExecutorService;

record AgenticanInternals(
        TaskStateStore taskStateStore,
        TaskListener taskListener,
        TaskRunner taskRunner,
        ExecutorService taskExecutor,
        TaskDecorator taskDecorator,
        HitlManager hitlManager,
        KnowledgeIngestor knowledgeIngestor) {
}
