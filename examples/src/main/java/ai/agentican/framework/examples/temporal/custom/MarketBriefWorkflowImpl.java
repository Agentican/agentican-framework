package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.examples.temporal.common.MarketBriefParams;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.temporal.activity.AgentStepActivity;
import ai.agentican.temporal.dto.AgentInvocationRequest;
import ai.agentican.temporal.dto.AgentInvocationResult;
import ai.agentican.temporal.dto.AgentResumeRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MarketBriefWorkflowImpl implements MarketBriefWorkflow {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentStepActivity agentStep = Workflow.newActivityStub(AgentStepActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(15)).build());

    private final Deque<List<ToolResult>> hitlReplies = new ArrayDeque<>();

    @Override
    public String run(MarketBriefParams params) {

        var taskId = Workflow.randomUUID().toString();

        var identifyReq = agentRequest("researcher",
                """
                Identify the top %d vendors in %s.
                Return a JSON array of vendor names — names only, no commentary.
                """.formatted(params.vendorCount(), params.topic()),
                taskId, "identify", List.of("web-search"));

        var identify = waitForCompletion(agentStep.invokeAgent(identifyReq), identifyReq);

        var vendorNames = parseVendorNames(identify.text());

        var deepDiveReqs    = new ArrayList<AgentInvocationRequest>();
        var deepDivePromises = new ArrayList<Promise<AgentInvocationResult>>();

        for (var vendorName : vendorNames) {

            final var vendor = vendorName;   // effectively-final for the lambda capture

            var analyzeReq = agentRequest("researcher",
                    """
                    Deep-dive vendor %s: positioning, key strengths, recent news.
                    Quote sources.
                    """.formatted(vendor),
                    taskId, "deep-dive/" + vendor, List.of("web-search"));

            deepDiveReqs.add(analyzeReq);
            deepDivePromises.add(Async.function(() -> agentStep.invokeAgent(analyzeReq)));
        }

        Promise.allOf(deepDivePromises).get();

        var deepDiveTexts = new ArrayList<String>();

        for (int i = 0; i < deepDivePromises.size(); i++) {

            var resolved = waitForCompletion(deepDivePromises.get(i).get(), deepDiveReqs.get(i));

            deepDiveTexts.add("Vendor: " + vendorNames.get(i) + "\n" + resolved.text());
        }

        var combinedDeepDives = String.join("\n\n---\n\n", deepDiveTexts);

        var classifyReq = agentRequest("writer",
                """
                Read the per-vendor deep-dives below. If any vendor has launched
                a competitive feature in the last 30 days, return the single word
                'urgent'. Otherwise return 'standard'.

                Deep-dives:
                %s
                """.formatted(combinedDeepDives),
                taskId, "classify", List.of());

        var classify = waitForCompletion(agentStep.invokeAgent(classifyReq), classifyReq);

        var isUrgent = classify.text().trim().equalsIgnoreCase("urgent");

        var deliverInstructions = isUrgent
                ? """
                  Synthesize a vendor brief flagged URGENT for executive review.
                  Lead with the recent competitive moves.

                  Topic: %s
                  Deep-dives:
                  %s
                  """.formatted(params.topic(), combinedDeepDives)
                : """
                  Synthesize a vendor brief.

                  Topic: %s
                  Deep-dives:
                  %s
                  """.formatted(params.topic(), combinedDeepDives);

        var deliverReq = agentRequest("writer", deliverInstructions, taskId,
                isUrgent ? "deliver/urgent-brief" : "deliver/standard-brief",
                List.of());

        var deliver = waitForCompletion(agentStep.invokeAgent(deliverReq), deliverReq);

        return deliver.text();
    }

    @Override
    public void provideHitlReply(List<ToolResult> toolResults) {

        hitlReplies.addLast(toolResults);
    }

    private AgentInvocationResult waitForCompletion(AgentInvocationResult result, AgentInvocationRequest req) {

        while (result.status() == AgentStatus.SUSPENDED) {

            Workflow.await(() -> !hitlReplies.isEmpty());

            var reply = hitlReplies.pollFirst();

            result = agentStep.resumeAgent(new AgentResumeRequest(req, result.runId(), reply));
        }

        return result;
    }

    private static AgentInvocationRequest agentRequest(String agentRef, String renderedTask,
                                                       String taskId, String stepName, List<String> skills) {

        return new AgentInvocationRequest(agentRef, renderedTask, taskId,
                Workflow.randomUUID().toString(), stepName, null, skills, List.of());
    }

    private static List<String> parseVendorNames(String json) {

        try {
            return JSON.readerForListOf(String.class).readValue(json);
        }
        catch (Exception e) {
            throw new IllegalStateException(
                    "identify step did not return a JSON array of vendor names: " + json, e);
        }
    }
}
