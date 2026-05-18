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
WorkflowStepAgent.builder("draft-email")
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

After the configured max retries (default 3, configurable via `WorkerConfig.maxStepRetries()`), the step fails. Individual steps can override this with `WorkflowStepAgent.builder().maxRetries(5)`.

## HitlCheckpoint

```java
record HitlCheckpoint(
    String id,
    HitlCheckpoint.Type type,   // TOOL_CALL, STEP_OUTPUT, or QUESTION
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

## Routing responses: `HitlResponseDispatcher`

The path "human responds via REST → orchestrator wakes up" needs to land the response in whichever runtime is actually doing the waiting. For an in-process task, the parked virtual thread lives in the same JVM and `HitlManager.respond(checkpointId, response)` completes its `CompletableFuture`. For a Temporal-owned task, the orchestrator is waiting on a Temporal workflow signal in some worker process — `HitlManager` has no parked thread to wake up there.

`HitlResponseDispatcher` is the SPI the REST layer uses so it doesn't need to know the difference:

```java
public interface HitlResponseDispatcher {
    void respond(String checkpointId, HitlResponse response);
    void cancel(String checkpointId);
}
```

The default `InProcessHitlResponseDispatcher` wraps `HitlManager` directly. Under Quarkus it's wired automatically via `AgenticanBeansProducer`. Both the HTTP endpoint (`POST /agentican/checkpoints/{id}/respond`) and the WebSocket `respond` action go through this dispatcher; existence checks ("is this checkpoint pending?") use `TaskEventBus.allPending()`, which is a runtime-agnostic projection of `HitlNotified` events.

### Routing to Temporal-owned checkpoints

When `agentican-temporal` is on the classpath and your task's runtime is `TEMPORAL`, replace the default dispatcher with `TemporalAwareHitlResponseDispatcher` (a composite that signals Temporal workflows for TEMPORAL checkpoints and delegates everything else to the in-process fallback):

```java
@Produces @ApplicationScoped @Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER + 10)
HitlResponseDispatcher temporalAwareDispatcher(HitlManager hm, WorkflowRunStore store,
                                               WorkflowClient client, AgenticanEventBus bus) {
    var d = new TemporalAwareHitlResponseDispatcher(
            new InProcessHitlResponseDispatcher(hm), store, client);
    bus.subscribe(d);   // indexes TEMPORAL checkpoints as HitlNotified events arrive
    return d;
}
```

The composite subscribes to the bus, watches for `HitlNotified` events with `runtime == TEMPORAL`, and stores `checkpointId → temporalWorkflowId` in a small in-memory index. On `respond` / `cancel`, it consults the index and either signals the workflow (`RunnerBasedAgentWorkflow.provideHitlResponse`) or falls back to the in-process path. From the REST layer's perspective the API is unchanged.

**Cancel semantics for TEMPORAL checkpoints**: Temporal has no native "drop this signal" operation, so the dispatcher synthesizes a `HitlResponse(approved=false, "Cancelled by operator")` and signals the workflow. The agent loop sees this as a rejection.

**Custom workflow types**: the dispatcher is hardcoded to signal `RunnerBasedAgentWorkflow.provideHitlResponse`. If you've authored a custom Temporal workflow class that emits HITL checkpoints, write a sibling dispatcher (the SPI is two methods).

## Next Steps

- [Tools & Toolkits](tools.md) — marking tools for HITL
- [Tasks & Steps](tasks.md) — step-level HITL
- [Examples](examples.md) — HITL recipes
