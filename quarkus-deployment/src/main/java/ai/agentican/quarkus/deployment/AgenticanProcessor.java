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
import ai.agentican.quarkus.AgenticanBeansProducer;
import ai.agentican.quarkus.AgenticanConfig;
import ai.agentican.quarkus.AgenticanProducer;
import ai.agentican.quarkus.AgentProducer;
import ai.agentican.quarkus.devui.AgenticanDevUIService;
import ai.agentican.quarkus.event.CdiEventBridge;
import ai.agentican.quarkus.health.AgenticanLivenessCheck;
import ai.agentican.quarkus.health.AgenticanReadinessCheck;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class AgenticanProcessor {

    private static final String FEATURE = "agentican";

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
                CdiEventBridge.class.getName(),
                AgenticanDevUIService.class.getName(),
                AgenticanConfig.class.getName(),
                AgenticanLivenessCheck.class.getName(),
                AgenticanReadinessCheck.class.getName());
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
