package ai.agentican.quarkus.devui;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.state.TaskStateStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AgenticanDevUIService {

    @Inject
    Agentican agentican;

    @Inject
    TaskStateStore taskStateStore;

    @Inject
    HitlManager hitlManager;

    @Inject
    KnowledgeStore knowledgeStore;

    public List<Map<String, Object>> getAgents() {

        return agentican.registry().agents().getAll().stream()
                .map(agent -> Map.<String, Object>of(
                        "name", agent.name(),
                        "role", agent.role()))
                .toList();
    }

    public List<Map<String, Object>> getSkills() {

        return agentican.registry().skills().getAll().stream()
                .map(skill -> Map.<String, Object>of(
                        "name", skill.name(),
                        "instructions", skill.instructions()))
                .toList();
    }

    public List<Map<String, Object>> getTasks() {

        return taskStateStore.list().stream()
                .map(log -> Map.<String, Object>of(
                        "taskId", log.taskId(),
                        "taskName", log.taskName(),
                        "status", log.status() != null ? log.status().name() : "RUNNING",
                        "inputTokens", log.inputTokens(),
                        "outputTokens", log.outputTokens()))
                .toList();
    }

    public List<Map<String, Object>> getCheckpoints() {

        return hitlManager.pendingCheckpoints().values().stream()
                .map(cp -> Map.<String, Object>of(
                        "id", cp.id(),
                        "type", cp.type().name(),
                        "stepName", cp.stepName(),
                        "description", cp.description()))
                .toList();
    }

    public List<Map<String, Object>> getKnowledge() {

        return knowledgeStore.all().stream()
                .map(entry -> Map.<String, Object>of(
                        "id", entry.id(),
                        "name", entry.name(),
                        "status", entry.status().name(),
                        "factCount", entry.facts().size()))
                .toList();
    }
}
