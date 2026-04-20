package ai.agentican.quarkus;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.registry.PlanRegistryMemory;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.store.TaskStateStoreMemory;
import ai.agentican.framework.store.TaskStateStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AgenticanBeansProducer {

    @Produces
    @ApplicationScoped
    @DefaultBean
    public HitlManager defaultHitlManager() {

        return new HitlManager(HitlNotifier.logging());
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public KnowledgeStore defaultKnowledgeStore() {

        return new KnowledgeStoreMemory();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public TaskStateStore defaultTaskStateStore() {

        return new TaskStateStoreMemory();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public AgentRegistry defaultAgentRegistry() {

        return new AgentRegistryMemory();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public SkillRegistry defaultSkillRegistry() {

        return new SkillRegistryMemory();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public PlanRegistry defaultPlanRegistry() {

        return new PlanRegistryMemory();
    }
}
