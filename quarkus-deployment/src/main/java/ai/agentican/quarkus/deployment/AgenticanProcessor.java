package ai.agentican.quarkus.deployment;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.WorkerConfig;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeFact;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeStatus;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.quarkus.AgentTask;
import ai.agentican.quarkus.AgenticanBeansProducer;
import ai.agentican.quarkus.AgenticanConfig;
import ai.agentican.quarkus.AgenticanProducer;
import ai.agentican.quarkus.AgenticanTaskProducer;
import ai.agentican.quarkus.AgentProducer;
import ai.agentican.quarkus.ReactiveAgenticanTaskProducer;
import ai.agentican.quarkus.WorkflowTask;
import ai.agentican.quarkus.devui.AgenticanDevUIService;
import ai.agentican.quarkus.event.CdiEventBridge;
import ai.agentican.quarkus.health.AgenticanLivenessCheck;
import ai.agentican.quarkus.health.AgenticanReadinessCheck;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

import org.jboss.jandex.DotName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

class AgenticanProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanProcessor.class);
    private static final String FEATURE = "agentican";

    private static final DotName AGENT_TASK_DOT    = DotName.createSimple(AgentTask.class.getName());
    private static final DotName WORKFLOW_TASK_DOT = DotName.createSimple(WorkflowTask.class.getName());

    @BuildStep
    FeatureBuildItem feature() {

        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {

        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        AgenticanProducer.class,
                        AgenticanBeansProducer.class,
                        AgentProducer.class,
                        AgenticanTaskProducer.class,
                        ReactiveAgenticanTaskProducer.class,
                        CdiEventBridge.class,
                        AgenticanDevUIService.class,
                        AgenticanLivenessCheck.class,
                        AgenticanReadinessCheck.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem indexRuntimeClasses() {

        return new AdditionalIndexedClassesBuildItem(
                AgenticanProducer.class.getName(),
                AgenticanBeansProducer.class.getName(),
                AgentProducer.class.getName(),
                AgenticanTaskProducer.class.getName(),
                ReactiveAgenticanTaskProducer.class.getName(),
                CdiEventBridge.class.getName(),
                AgenticanDevUIService.class.getName(),
                AgenticanConfig.class.getName(),
                AgenticanLivenessCheck.class.getName(),
                AgenticanReadinessCheck.class.getName());
    }

    /**
     * Validates {@code @AgentTask} and {@code @WorkflowTask} injection points at build
     * time: warns when an {@code agent} or {@code skills} reference isn't declared in
     * {@code agentican.agents} / {@code agentican.skills}. Missing plans are not
     * checked — plans can be registered at runtime (YAML, DB hydration, programmatic).
     */
    @BuildStep
    void validateTaskInjectionPoints(CombinedIndexBuildItem indexItem, AgenticanConfig config) {

        var index = indexItem.getIndex();

        var declaredAgents = new HashSet<String>();
        config.agents().forEach(a -> {
            declaredAgents.add(a.name());
            a.externalId().ifPresent(declaredAgents::add);
        });

        var declaredSkills = new HashSet<String>();
        config.skills().forEach(s -> {
            declaredSkills.add(s.name());
            s.externalId().ifPresent(declaredSkills::add);
        });

        for (var ann : index.getAnnotations(AGENT_TASK_DOT)) {

            var agent = ann.value("agent").asString();

            if (!agent.isEmpty() && !declaredAgents.isEmpty() && !declaredAgents.contains(agent))
                LOG.warn("@AgentTask at {} references agent '{}' not declared in agentican.agents; "
                        + "will fail at bean resolution unless registered programmatically",
                        ann.target(), agent);

            var skillsValue = ann.value("skills");

            if (skillsValue != null && !declaredSkills.isEmpty()) {

                for (var skill : skillsValue.asStringArray()) {
                    if (!skill.isEmpty() && !declaredSkills.contains(skill))
                        LOG.warn("@AgentTask at {} references skill '{}' not declared in agentican.skills",
                                ann.target(), skill);
                }
            }
        }

        for (var ann : index.getAnnotations(WORKFLOW_TASK_DOT)) {

            var planName = ann.value("plan").asString();

            if (!planName.isEmpty())
                LOG.debug("@WorkflowTask at {} references plan '{}'; build-time validation skipped "
                        + "(plans may be registered at runtime)", ann.target(), planName);
        }
    }

    @BuildStep
    ReflectiveClassBuildItem registerFrameworkForReflection() {

        return ReflectiveClassBuildItem.builder(
                RuntimeConfig.class,
                LlmConfig.class,
                AgentConfig.class,
                McpConfig.class,
                ComposioConfig.class,
                WorkerConfig.class,
                SkillConfig.class,
                PlanConfig.class,
                Plan.class,
                PlanStep.class,
                PlanStepAgent.class,
                PlanStepLoop.class,
                PlanStepBranch.class,
                PlanStepBranch.Path.class,
                PlanParam.class,
                TaskStatus.class,
                LlmRequest.class,
                LlmResponse.class,
                ToolCall.class,
                StopReason.class,
                HitlCheckpoint.class,
                HitlCheckpoint.Type.class,
                HitlResponse.class,
                KnowledgeEntry.class,
                KnowledgeFact.class,
                KnowledgeStatus.class)
                .methods()
                .fields()
                .build();
    }
}
