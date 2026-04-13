# Human in the Loop (HITL)

Agentican supports three kinds of human-in-the-loop interactions:

1. **Tool approval** — pause before executing a sensitive tool (e.g., sending an email)
2. **Question** — agent asks the user a question and waits for an answer
3. **Step approval** — pause after a step completes, let the user approve or reject the output (with feedback for retry)

All three use the same checkpoint mechanism via `HitlManager`.

## How It Works

When a task hits an HITL gate, the framework:

1. Creates a `HitlCheckpoint` (a record with id, type, description, content)
2. Calls your `HitlNotifier.onCheckpoint(manager, checkpoint)` callback
3. **Parks the virtual thread** waiting for `manager.respond(checkpointId, response)`
4. When you respond, the thread unparks and execution resumes

Because Agentican uses virtual threads exclusively, parking a task waiting on human approval costs almost nothing — the carrier OS thread is released immediately. You can have thousands of tasks waiting for hours-long approvals without any thread pool exhaustion.

## Setting Up HITL

Create a `HitlManager` with a notifier — a callback that's invoked when a checkpoint is created:

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    System.out.println("Checkpoint: " + checkpoint.description());
    System.out.print("Approve? (y/n): ");

    var line = new Scanner(System.in).nextLine();

    var response = line.equalsIgnoreCase("y")
            ? HitlResponse.approve()
            : HitlResponse.reject("User declined");

    mgr.respond(checkpoint.id(), response);
});

try (var agentican = Agentican.builder()
        .config(config)
        .hitlManager(hitlManager)
        .build()) {

    agentican.run("Send a follow-up email").result();
}
```

If you don't provide a `HitlManager`, Agentican creates a default one that auto-approves with a logging notifier.

## Tool Approval

Mark a tool as requiring approval by setting its `HitlType`:

```java
new ToolRecord(
    "send_email", "Send an email",
    Map.of("to", ..., "subject", ..., "body", ...),
    List.of("to", "subject", "body"),
    HitlType.APPROVAL  // ← human approval required
)
```

When the agent calls this tool, the framework:

1. Stops the agent loop before executing
2. Creates a `TOOL_CALL` checkpoint with the tool name and args
3. Calls your notifier
4. Waits for `respond()`

If approved (`HitlResponse.approve()`):
- The tool executes with the original arguments
- The result is returned to the agent
- The loop continues normally

If rejected (`HitlResponse.reject(feedback)`):
- The tool is **not** executed
- The agent receives a tool result containing the rejection and feedback
- The agent decides what to do next (try a different approach, ask the user, etc.)

## Questions (ASK_QUESTION)

The built-in `AskQuestionToolkit` lets agents ask the user for information mid-workflow.

The agent calls `ASK_QUESTION` like any tool:

```json
{
  "tool": "ASK_QUESTION",
  "args": {
    "question": "Which timezone should the meeting be in?",
    "context": "I'm scheduling a call but need to know your preference"
  }
}
```

The framework:
1. Creates a `QUESTION` checkpoint with the question text
2. Calls your notifier
3. Waits for the user's answer

You respond with the answer in the `feedback` field:

```java
mgr.respond(checkpoint.id(), HitlResponse.approve("Pacific Time"));
```

The agent receives `{"question": "...", "answer": "Pacific Time"}` as the tool result and continues with that knowledge.

## Step Approval

Mark a task step with `hitl=true` to require approval after the step completes:

```java
PlanStepAgent.builder("draft-email")
    .agent("writer")
    .instructions("Draft a polite follow-up email")
    .hitl(true)  // ← human approval required after this step
    .build();
```

After the step completes:
1. The framework creates a `STEP_OUTPUT` checkpoint with the step's output
2. Calls your notifier
3. Waits for response

If approved, the task continues. If rejected, the step is retried with the rejection feedback added to its instructions:

```
... original instructions ...

## Reviewer Feedback

A previous attempt at this step was rejected by the reviewer. Please address the following feedback:
<reviewer-feedback>
[user feedback here]
</reviewer-feedback>
```

After the configured max retries (default 3, configurable via `WorkerConfig.maxStepRetries()`), the step fails. Individual steps can override this with `PlanStepAgent.builder().maxRetries(5)`.

## HitlCheckpoint

```java
record HitlCheckpoint(
    String id,
    HitlCheckpointType type,    // TOOL_CALL, STEP_OUTPUT, or QUESTION
    String stepName,
    String description,         // human-readable summary
    String content              // type-specific payload (tool args, step output, question)
)
```

In your notifier, switch on `type` if you need to render different UIs for different checkpoint types.

## HitlResponse

```java
record HitlResponse(boolean approved, String feedback)

HitlResponse.approve()                    // approve, no feedback
HitlResponse.approve("yes, ship it")      // approve with feedback / answer
HitlResponse.reject("not ready yet")      // reject with reason
```

For `QUESTION` checkpoints, the `feedback` field carries the user's answer (always treated as "approve" — there's no rejecting a question).

## Notifier Patterns

### Synchronous (CLI / Tests)

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {
    var input = readUserInput(checkpoint);
    mgr.respond(checkpoint.id(), input);
});
```

The notifier responds inline. The framework guarantees that synchronous responses work — the future is captured before the notifier is called.

### Async (Web app / REST API)

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    // Store the checkpoint somewhere your UI can poll
    pendingCheckpoints.put(checkpoint.id(), checkpoint);

    // Notify the user via WebSocket, push notification, email, etc.
    notifyUser(checkpoint);

    // Don't call respond() here — your REST endpoint will call it later
});

// In your REST controller:
@PostMapping("/checkpoints/{id}/approve")
public void approve(@PathVariable String id, @RequestBody String feedback) {
    hitlManager.respond(id, HitlResponse.approve(feedback));
}
```

The task's virtual thread parks until the REST call completes the checkpoint.

### Timeout

`HitlManager` has a default timeout of 30 minutes. After that, the checkpoint is auto-rejected with a "timed out" message and the agent receives the rejection.

Configure a different timeout:

```java
var hitlManager = new HitlManager(notifier, Duration.ofHours(24));
```

## Multiple Concurrent Checkpoints

If multiple tasks have HITL checkpoints active at the same time, the framework handles them independently. Each task's virtual thread parks on its own checkpoint. When you respond to one, only that task resumes.

Within a single task, suspended steps are processed one at a time. If two parallel steps both suspend, the runner handles them sequentially after all running steps finish.

## Next Steps

- [Tools & Toolkits](tools.md) — marking tools for HITL
- [Tasks & Steps](tasks.md) — step-level HITL
- [Examples](examples.md) — HITL recipes
