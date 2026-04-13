package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.orchestration.model.*;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Parallel;
import ai.agentican.framework.util.Placeholders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class StepLoopRunner {

    private static final Logger LOG = LoggerFactory.getLogger(StepLoopRunner.class);

    @FunctionalInterface
    interface SubPlanRunner {

        TaskResult run(Plan plan, Map<String, String> params, AtomicBoolean cancelled, Map<String, String> outputs);
    }

    private final SubPlanRunner subPlanRunner;

    StepLoopRunner(SubPlanRunner subPlanRunner) {

        this.subPlanRunner = subPlanRunner;
    }

    TaskStepResult run(PlanStepLoop step, Map<String, String> outputs, Map<String, String> params,
                       AtomicBoolean cancelled) {

        var upstreamOutput = outputs.get(step.over());

        if (upstreamOutput == null) {

            return new TaskStepResult(step.name(), TaskStatus.FAILED,
                    "No output found from step: " + step.over(), List.of());
        }

        var items = Json.findArray(upstreamOutput);

        if (items.isEmpty()) {

            LOG.warn("Loop step '{}': no items found in upstream output", step.name());
            return new TaskStepResult(step.name(), TaskStatus.COMPLETED, "", List.of());
        }

        LOG.info(Logs.RUNNER_RUN_LOOP_STEP, step.name(), items.size());

        record IndexedItem(int index, String item) {}

        var indexedItems = new ArrayList<IndexedItem>();

        for (int i = 0; i < items.size(); i++)
            indexedItems.add(new IndexedItem(i, items.get(i)));

        var results = Parallel.map(indexedItems, indexed -> {

            if (cancelled.get()) return null;

            var resolvedBody = resolveLoopBody(step.body(), indexed.item(), params);

            var subPlan = new Plan(
                    null, step.name() + "-iter-" + (indexed.index() + 1), "", List.of(), resolvedBody);

            LOG.info(Logs.RUNNER_RUN_LOOP_STEP_ITEM, step.name(), indexed.index() + 1);

            return subPlanRunner.run(subPlan, params, cancelled, outputs);
        });

        var iterationResults = new LinkedHashMap<Integer, TaskResult>();

        for (int i = 0; i < results.size(); i++)
            iterationResults.put(i, results.get(i));

        return aggregateResults(step.name(), items.size(), iterationResults);
    }

    private TaskStepResult aggregateResults(String stepName, int itemCount,
                                             Map<Integer, TaskResult> iterationResults) {

        var aggregated = new StringBuilder();
        var allAgentResults = new ArrayList<AgentResult>();

        for (int i = 0; i < itemCount; i++) {

            if (i > 0) aggregated.append("\n\n");

            var subResult = iterationResults.get(i);

            aggregated.append("## Iteration ").append(i + 1).append("\n\n");

            if (subResult != null && !subResult.stepResults().isEmpty()) {

                var lastStep = subResult.stepResults().getLast();
                aggregated.append(lastStep.output() != null ? lastStep.output() : "");

                subResult.stepResults().stream()
                        .flatMap(sr -> sr.agentResults().stream())
                        .forEach(allAgentResults::add);
            }
        }

        var allCompleted = iterationResults.values().stream()
                .allMatch(r -> r != null && r.status() == TaskStatus.COMPLETED);
        var status = allCompleted ? TaskStatus.COMPLETED : TaskStatus.FAILED;

        return new TaskStepResult(stepName, status, aggregated.toString(), allAgentResults);
    }

    // --- Item/Loop Body Resolution ---

    List<PlanStep> resolveLoopBody(List<PlanStep> body, String item, Map<String, String> params) {

        return body.stream().map(step -> (PlanStep) switch (step) {

            case PlanStepAgent s -> new PlanStepAgent(
                    s.name(), s.agentName(),
                    Placeholders.resolveParams(Placeholders.resolveItem(s.instructions(), item), params),
                    s.dependencies(), s.hitl(), s.skills(), s.toolkits());

            case PlanStepLoop s -> new PlanStepLoop(
                    s.name(), s.over(),
                    resolveLoopBody(s.body(), item, params),
                    s.dependencies(), s.hitl());

            case PlanStepBranch s -> new PlanStepBranch(
                    s.name(), s.from(),
                    s.paths().stream().map(p -> new PlanStepBranch.Path(
                            p.pathName(),
                            resolveLoopBody(p.body(), item, params))).toList(),
                    s.defaultPath(), s.dependencies(), s.hitl());
        }).toList();
    }

}
