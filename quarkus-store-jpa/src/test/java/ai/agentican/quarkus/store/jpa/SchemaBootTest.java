package ai.agentican.quarkus.store.jpa;

import ai.agentican.quarkus.store.jpa.entity.AgentEntity;
import ai.agentican.quarkus.store.jpa.entity.KnowledgeEntryEntity;
import ai.agentican.quarkus.store.jpa.entity.KnowledgeFactEntity;
import ai.agentican.quarkus.store.jpa.entity.PlanEntity;
import ai.agentican.quarkus.store.jpa.entity.SkillEntity;
import ai.agentican.quarkus.store.jpa.entity.TaskEntity;
import ai.agentican.quarkus.store.jpa.entity.TaskStepEntity;
import ai.agentican.quarkus.store.jpa.entity.ToolEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class SchemaBootTest {

    @Test
    @Transactional
    void canPersistCatalogEntities() {

        var agent = new AgentEntity();
        agent.id = "agent-" + UUID.randomUUID();
        agent.name = "Researcher";
        agent.role = "Senior researcher";
        agent.createdAt = Instant.now();
        agent.updatedAt = agent.createdAt;
        agent.persist();

        var skill = new SkillEntity();
        skill.id = "skill-" + UUID.randomUUID();
        skill.name = "Source Triangulation";
        skill.instructions = "cross-verify claims";
        skill.createdAt = Instant.now();
        skill.updatedAt = skill.createdAt;
        skill.persist();

        var tool = new ToolEntity();
        tool.id = "tool-" + UUID.randomUUID();
        tool.name = "web_search";
        tool.description = "Search the web";
        tool.source = "builtin";
        tool.createdAt = Instant.now();
        tool.persist();

        assertNotNull(AgentEntity.findById(agent.id));
        assertNotNull(SkillEntity.findById(skill.id));
        assertNotNull(ToolEntity.findById(tool.id));
    }

    @Test
    @Transactional
    void canPersistPlanWithDefinitionJson() {

        var plan = new PlanEntity();
        plan.id = "plan-" + UUID.randomUUID();
        plan.name = "Research Plan";
        plan.description = "A plan";
        plan.definitionJson = "{\"id\":\"plan-x\",\"name\":\"Research Plan\",\"steps\":[]}";
        plan.createdAt = Instant.now();
        plan.persist();

        var fetched = (PlanEntity) PlanEntity.findById(plan.id);
        assertNotNull(fetched);
        assertEquals("Research Plan", fetched.name);
        assertNotNull(fetched.definitionJson);
    }

    @Test
    @Transactional
    void canPersistTaskExecutionGraphWithFkToPlan() {

        var plan = new PlanEntity();
        plan.id = "plan-ex-" + UUID.randomUUID();
        plan.name = "Execution Plan";
        plan.definitionJson = "{}";
        plan.createdAt = Instant.now();
        plan.persist();

        var task = new TaskEntity();
        task.id = "task-" + UUID.randomUUID();
        task.planId = plan.id;
        task.taskName = "a task";
        task.iterationIndex = 0;
        task.status = "RUNNING";
        task.createdAt = Instant.now();
        task.persist();

        var taskStep = new TaskStepEntity();
        taskStep.id = "ts-" + UUID.randomUUID();
        taskStep.taskId = task.id;
        taskStep.stepName = "s1";
        taskStep.status = "RUNNING";
        taskStep.createdAt = Instant.now();
        taskStep.persist();

        var subTask = new TaskEntity();
        subTask.id = "sub-" + UUID.randomUUID();
        subTask.planId = plan.id;
        subTask.parentTaskId = task.id;
        subTask.parentStepId = taskStep.id;
        subTask.iterationIndex = 0;
        subTask.status = "RUNNING";
        subTask.createdAt = Instant.now();
        subTask.persist();

        assertNotNull(TaskEntity.findById(subTask.id));
        assertNotNull(TaskStepEntity.findById(taskStep.id));
    }

    @Test
    @Transactional
    void canPersistKnowledgeEntryAndFacts() {

        var entry = new KnowledgeEntryEntity();
        entry.id = "k-" + UUID.randomUUID();
        entry.name = "Claude Opus 4.6";
        entry.description = "frontier model";
        entry.status = "INDEXED";
        entry.createdAt = Instant.now();
        entry.updatedAt = entry.createdAt;
        entry.persist();

        var fact = new KnowledgeFactEntity();
        fact.id = "f-" + UUID.randomUUID();
        fact.entryId = entry.id;
        fact.name = "Pricing";
        fact.content = "$15/$75 per million tokens";
        fact.tagsJson = "[\"anthropic/claude/pricing\"]";
        fact.createdAt = Instant.now();
        fact.updatedAt = fact.createdAt;
        fact.persist();

        assertEquals(1, KnowledgeFactEntity.count("entryId", entry.id));
    }
}
