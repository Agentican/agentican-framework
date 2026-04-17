package ai.agentican.quarkus;

import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.InMemoryAgentRegistry;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.knowledge.MemKnowledgeStore;
import ai.agentican.framework.orchestration.InMemoryPlanRegistry;
import ai.agentican.framework.orchestration.PlanRegistry;
import ai.agentican.framework.skill.InMemorySkillRegistry;
import ai.agentican.framework.skill.SkillRegistry;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.state.TaskStateStore;
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

        return new MemKnowledgeStore();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public TaskStateStore defaultTaskStateStore() {

        return new MemTaskStateStore();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public AgentRegistry defaultAgentRegistry() {

        return new InMemoryAgentRegistry();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public SkillRegistry defaultSkillRegistry() {

        return new InMemorySkillRegistry();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public PlanRegistry defaultPlanRegistry() {

        return new InMemoryPlanRegistry();
    }
}
