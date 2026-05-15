package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.CodeStepContext;
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.store.WorkflowRunStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class StepCodeRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepCodeRunner.class);

    private final CodeStepRegistry codeStepRegistry;
    private final WorkflowRunStore workflowRunStore;
    private final HitlManager hitlManager;

    StepCodeRunner(CodeStepRegistry codeStepRegistry,
                   WorkflowRunStore workflowRunStore, HitlManager hitlManager) {

        this.codeStepRegistry = codeStepRegistry;
        this.workflowRunStore = workflowRunStore;
        this.hitlManager = hitlManager;
    }

    WorkflowStepResult run(WorkflowStepCode<?> taskStep, Map<String, String> parentStepOutputs,
                       Map<String, String> taskParams, AtomicBoolean cancelled,
                       String taskId, String stepId) {

        var slug = taskStep.codeSlug();
        var registered = codeStepRegistry.get(slug);

        if (registered == null) {

            var message = "No code step registered for slug '" + slug + "'";

            LOG.error(message);

            return new WorkflowStepResult(taskStep.name(), WorkflowRunStatus.FAILED, message, List.of());
        }

        LOG.info("Running code step '{}' (slug={})", taskStep.name(), slug);

        var spec = registered.spec();
        var inputType = spec.inputType();
        var outputType = spec.outputType();

        try {

            var typedInput = OrchestrationHelpers.resolveInput(taskStep.input(), inputType, taskParams, parentStepOutputs);

            var context = new CodeStepContext(taskId, stepId, cancelled, workflowRunStore, hitlManager);

            @SuppressWarnings({"unchecked", "rawtypes"})
            var output = ((CodeStep) registered.executor()).execute(typedInput, context);

            var stored = OrchestrationHelpers.serializeOutput(output, outputType);

            return new WorkflowStepResult(taskStep.name(), WorkflowRunStatus.COMPLETED, stored, List.of());

        } catch (RuntimeException e) {

            LOG.error("Code step '{}' (slug={}) threw: {}", taskStep.name(), slug, e.getMessage(), e);

            return new WorkflowStepResult(taskStep.name(), WorkflowRunStatus.FAILED,
                    "Error: " + e.getMessage(), List.of(), e);
        }
    }

}
