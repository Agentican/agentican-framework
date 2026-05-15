package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.examples.temporal.common.IdentifyVendorsInput;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.Message;
import ai.agentican.temporal.activity.LlmCallActivity;
import ai.agentican.temporal.activity.ToolCallActivity;
import ai.agentican.temporal.dto.ToolCallRequest;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class IdentifyVendorsWorkflowImpl implements IdentifyVendorsWorkflow {

    private static final Duration LLM_TIMEOUT  = Duration.ofMinutes(5);
    private static final Duration TOOL_TIMEOUT = Duration.ofMinutes(2);

    private final LlmCallActivity llm = Workflow.newActivityStub(
            LlmCallActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(LLM_TIMEOUT).build());

    private final ToolCallActivity tool = Workflow.newActivityStub(
            ToolCallActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(TOOL_TIMEOUT).build());

    @Override
    public String run(IdentifyVendorsInput input) {

        var messages = new ArrayList<Message>();

        for (int turn = 0; turn < input.maxTurns(); turn++) {

            var request = new LlmRequest(input.systemPrompt(), input.userTask(), "", input.tools(),
                    turn, input.llmName(), null, null, null, messages);

            var response = llm.send(request);

            if (response.toolCalls() == null || response.toolCalls().isEmpty())
                return response.text();

            var assistantBlocks = new ArrayList<Message.Block>();

            if (response.text() != null && !response.text().isBlank())
                assistantBlocks.add(new Message.TextBlock(response.text()));

            for (var call : response.toolCalls())
                assistantBlocks.add(new Message.ToolUseBlock(call.id(), call.name(), call.args()));

            messages.add(new Message(Message.Role.ASSISTANT, List.copyOf(assistantBlocks)));

            var promises = new ArrayList<Promise<TurnToolResult>>();

            for (var call : response.toolCalls()) {

                final var c = call;

                promises.add(Async.function(() -> {

                    var output = tool.execute(new ToolCallRequest(c.name(), c.args()));

                    return new TurnToolResult(c.id(), output);
                }));
            }

            Promise.allOf(promises).get();

            var userBlocks = new ArrayList<Message.Block>();

            for (var p : promises) {

                var r = p.get();

                userBlocks.add(new Message.ToolResultBlock(r.toolUseId(), r.content(), false));
            }

            messages.add(new Message(Message.Role.USER, List.copyOf(userBlocks)));
        }

        throw new IllegalStateException(
                "identify loop exceeded maxTurns=" + input.maxTurns() + " without a terminal response");
    }

    private record TurnToolResult(String toolUseId, String content) { }
}
