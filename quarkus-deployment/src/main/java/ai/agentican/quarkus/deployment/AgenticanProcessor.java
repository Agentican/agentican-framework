package ai.agentican.quarkus.deployment;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.CatalogConfig;
import ai.agentican.framework.config.EngineConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.config.WorkflowConfig;
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
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.quarkus.Task;
import ai.agentican.quarkus.AgenticanBeansProducer;
import ai.agentican.quarkus.AgenticanConfig;
import ai.agentican.quarkus.AgenticanProducer;
import ai.agentican.quarkus.WorkflowProducer;
import ai.agentican.quarkus.AgentProducer;
import ai.agentican.quarkus.ReactiveWorkflowProducer;
import ai.agentican.quarkus.Workflow;
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

    private static final DotName TASK_DOT    = DotName.createSimple(Task.class.getName());
    private static final DotName WORKFLOW_DOT = DotName.createSimple(ai.agentican.quarkus.Workflow.class.getName());

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
                        WorkflowProducer.class,
                        ReactiveWorkflowProducer.class,
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
                WorkflowProducer.class.getName(),
                ReactiveWorkflowProducer.class.getName(),
                CdiEventBridge.class.getName(),
                AgenticanDevUIService.class.getName(),
                AgenticanConfig.class.getName(),
                AgenticanLivenessCheck.class.getName(),
                AgenticanReadinessCheck.class.getName());
    }

    @BuildStep
    void logTaskInjectionPoints(CombinedIndexBuildItem indexItem) {

        var index = indexItem.getIndex();

        for (var ann : index.getAnnotations(TASK_DOT))
            LOG.debug("@Task at {} references agent '{}'; resolution happens at runtime",
                    ann.target(), ann.value("agent").asString());

        for (var ann : index.getAnnotations(WORKFLOW_DOT))
            LOG.debug("@ai.agentican.quarkus.Workflow at {} references definition '{}'; resolution happens at runtime",
                    ann.target(), ann.value("definition").asString());
    }

    @BuildStep
    ReflectiveClassBuildItem registerFrameworkForReflection() {

        return ReflectiveClassBuildItem.builder(
                EngineConfig.class,
                CatalogConfig.class,
                LlmConfig.class,
                AgentConfig.class,
                McpConfig.class,
                ComposioConfig.class,
                WorkerConfig.class,
                SkillConfig.class,
                WorkflowConfig.class,
                WorkflowDefinition.class,
                WorkflowStep.class,
                WorkflowStepAgent.class,
                WorkflowStepLoop.class,
                WorkflowStepBranch.class,
                WorkflowStepBranch.Path.class,
                WorkflowParam.class,
                WorkflowRunStatus.class,
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
