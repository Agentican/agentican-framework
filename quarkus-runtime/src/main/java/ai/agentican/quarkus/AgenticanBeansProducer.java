package ai.agentican.quarkus;

import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.hitl.HitlResponseDispatcher;
import ai.agentican.framework.hitl.InProcessHitlResponseDispatcher;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.registry.WorkflowRegistryMemory;
import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.store.WorkflowRunStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AgenticanBeansProducer {

    @Produces
    @ApplicationScoped
    @DefaultBean
    public HitlManager defaultHitlManager(Instance<HitlNotifier> syncNotifier,
                                          Instance<ReactiveHitlNotifier> reactiveNotifier) {

        if (syncNotifier.isResolvable())
            return new HitlManager(syncNotifier.get());

        if (reactiveNotifier.isResolvable())
            return new HitlManager(ReactiveHitlNotifier.toSync(reactiveNotifier.get()));

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
    public WorkflowRunStore defaultTaskStateStore() {

        return new WorkflowRunStoreMemory();
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
    public WorkflowRegistry defaultPlanRegistry() {

        return new WorkflowRegistryMemory();
    }

    /**
     * In-process dispatcher — hands HITL responses to the local {@link HitlManager}.
     * When an external runtime (e.g. Temporal) needs to receive responses for tasks
     * it owns, register an {@code @Alternative @Priority(...)} bean that composes
     * over this default; see {@code TemporalAwareHitlResponseDispatcher}.
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public HitlResponseDispatcher defaultHitlResponseDispatcher(HitlManager hitlManager) {

        return new InProcessHitlResponseDispatcher(hitlManager);
    }
}
