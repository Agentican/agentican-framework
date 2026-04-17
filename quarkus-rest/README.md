# Agentican Quarkus REST

REST + SSE + HITL bridge for Agentican on Quarkus. Layers HTTP endpoints on top of
[`agentican-quarkus`](../quarkus) so a UI or any HTTP client can submit tasks, stream
progress in real time, and respond to human-in-the-loop checkpoints.

## What's in the box

- **Task lifecycle endpoints** — submit, list, inspect, cancel
- **Server-Sent Events** for live task progress streaming
- **HITL bridge** — list pending checkpoints and respond over HTTP. The framework's
  parked virtual thread wakes up automatically when you POST a response.
- **Agent listing** — discover what's registered
- **Structured error responses** with stable error codes
- **OpenAPI** auto-generated at `/q/openapi`, browseable at `/q/swagger-ui`

## Add to your Quarkus app

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-rest</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Add to `application.properties`:

```properties
# Required: index agentican-quarkus so its CDI beans are visible to this app
quarkus.index-dependency.agentican-quarkus.group-id=ai.agentican
quarkus.index-dependency.agentican-quarkus.artifact-id=agentican-quarkus-runtime

# Standard Agentican config (see agentican-quarkus-runtime README)
agentican.llm[0].api-key=${ANTHROPIC_API_KEY}
agentican.llm[0].model=claude-sonnet-4-5
agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding information
agentican.agents[0].external-id=researcher
```

That's it — the REST endpoints are auto-discovered.

## REST surface

All endpoints are mounted under `/agentican`. Override with `quarkus.http.root-path` if
you want a global prefix.

### Tasks

#### `POST /agentican/tasks` — submit a task

Two submission modes:

**Planner mode** — let the framework's `TaskPlannerAgent` build the task graph from a
natural-language description:

```bash
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{"description": "Find papers on agent frameworks"}'
```

```json
{ "taskId": "abc12345" }
```

**Pre-built mode** — supply a fully-specified `Task` graph:

```bash
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{
    "task": {
      "name": "research-task",
      "description": "Research X",
      "params": [{"name": "topic", "required": true}],
      "steps": [
        {
          "type": "agent",
          "name": "research",
          "agentName": "researcher",
          "instructions": "Find papers about ${topic}",
          "dependencies": [],
          "hitl": false,
          "skills": [],
          "toolkits": []
        }
      ]
    },
    "inputs": { "topic": "agents" }
  }'
```

Step types are tagged via the `type` discriminator: `"agent"`, `"loop"`, or `"branch"`.
Each maps to a framework `TaskStep` subtype.

The endpoint returns synchronously; the actual task runs on a virtual thread.
**You can immediately stream, cancel, or inspect the task using the returned `taskId`.**

#### `GET /agentican/tasks` — list tasks

```bash
curl 'http://localhost:8080/agentican/tasks?limit=20'
```

```json
[
  {
    "taskId": "abc12345",
    "taskName": "research-task",
    "status": "COMPLETED",
    "inputTokens": 1234,
    "outputTokens": 567,
    "cacheReadTokens": 0,
    "cacheWriteTokens": 0
  }
]
```

`?limit=N` (default 100, max 500). Sources the underlying `TaskStateStore` —
in-memory by default, Postgres-backed when `agentican-quarkus-store-jpa` is on
the classpath, or whatever bean you produce yourself.

#### `GET /agentican/tasks/{taskId}` — task summary

Same shape as the list elements above. Returns 404 if unknown.

#### `GET /agentican/tasks/{taskId}/log` — full task log

Includes per-step status, output, token counts, and per-LLM-run breakdowns:

```json
{
  "taskId": "abc12345",
  "taskName": "research-task",
  "status": "COMPLETED",
  "params": { "topic": "agents" },
  "steps": [
    {
      "stepName": "research",
      "status": "COMPLETED",
      "output": "...",
      "runCount": 1,
      "inputTokens": 1234,
      "outputTokens": 567,
      "cacheReadTokens": 0,
      "cacheWriteTokens": 0,
      "pendingCheckpointId": null,
      "runs": [
        {
          "index": 0,
          "startedAt": "2026-04-09T20:00:00Z",
          "completedAt": "2026-04-09T20:00:05Z",
          "turns": 3,
          "inputTokens": 1234,
          "outputTokens": 567,
          "cacheReadTokens": 0,
          "cacheWriteTokens": 0,
          "webSearchRequests": 0
        }
      ]
    }
  ],
  "inputTokens": 1234,
  "outputTokens": 567,
  "cacheReadTokens": 0,
  "cacheWriteTokens": 0
}
```

#### `DELETE /agentican/tasks/{taskId}` — cancel

Cancellation is cooperative — the framework checks the cancel flag between steps and
during HITL waits. Returns 204 immediately. Returns 404 if no in-flight handle exists
for that ID (the task may already be done).

#### `GET /agentican/tasks/{taskId}/stream` — SSE event stream

Live event stream for one task. Each event has an SSE `event:` name so browser clients
can dispatch:

```js
const es = new EventSource('/agentican/tasks/abc12345/stream');

es.addEventListener('task_started', e => console.log('started', JSON.parse(e.data)));
es.addEventListener('step_completed', e => render(JSON.parse(e.data)));
es.addEventListener('hitl_checkpoint', e => promptUser(JSON.parse(e.data)));
es.addEventListener('task_completed', e => {
    console.log('done', JSON.parse(e.data));
    es.close();
});
```

**Event types:**

| Name | Payload | Description |
|---|---|---|
| `task_started` | `TaskStartedEvent` | Fires once when the task begins |
| `step_completed` | `StepCompletedEvent` | Fires once per step that reaches a terminal state |
| `hitl_checkpoint` | `HitlCheckpointEvent` | Fires when a step parks on a HITL checkpoint |
| `task_completed` | `TaskCompletedEvent` | Fires once when the task reaches a terminal state |
| `heartbeat` | (comment) | Fires every 30s to keep proxies from closing the connection |

The stream is **hot** — late subscribers see events from the moment they subscribe, not
the full history. Use `GET /tasks/{id}/log` for history.

### Checkpoints (HITL bridge)

#### `GET /agentican/checkpoints` — all pending

```bash
curl http://localhost:8080/agentican/checkpoints
```

```json
{
  "abc12345": [
    {
      "id": "tool-send_email-...",
      "type": "TOOL_CALL",
      "stepName": "notify",
      "description": "Tool call: send_email",
      "content": "{\"to\":\"user@example.com\",...}"
    }
  ]
}
```

#### `GET /agentican/checkpoints/{taskId}` — pending for one task

Returns the same checkpoint records, scoped to one task.

#### `POST /agentican/checkpoints/{checkpointId}/respond` — respond

The killer feature. The framework's `HitlManager.awaitResponse(...)` parks a virtual
thread; this endpoint completes that thread's future and the task continues:

```bash
# Approve
curl -X POST http://localhost:8080/agentican/checkpoints/tool-send_email-.../respond \
  -H "content-type: application/json" \
  -d '{"approved": true}'

# Approve with feedback (e.g. for QUESTION-type checkpoints, the answer)
curl -X POST http://localhost:8080/agentican/checkpoints/question-.../respond \
  -H "content-type: application/json" \
  -d '{"approved": true, "feedback": "Pacific time"}'

# Reject with feedback
curl -X POST http://localhost:8080/agentican/checkpoints/tool-send_email-.../respond \
  -H "content-type: application/json" \
  -d '{"approved": false, "feedback": "wrong recipient"}'
```

Returns 204. The parked task wakes up immediately.

#### `POST /agentican/checkpoints/{checkpointId}/cancel`

Resolves a pending checkpoint as rejected. Useful when the user navigates away or you
want to abort a HITL gate without an explicit reject reason.

### Knowledge

#### `POST /agentican/knowledge` — create entry

```bash
curl -X POST http://localhost:8080/agentican/knowledge \
  -H "content-type: application/json" \
  -d '{"topic": "Agent Frameworks", "summary": "Overview of modern agent frameworks"}'
```

```json
{ "id": "k-abc123", "topic": "Agent Frameworks", "summary": "Overview of modern agent frameworks", "facts": [] }
```

#### `GET /agentican/knowledge` — list entries

```bash
curl http://localhost:8080/agentican/knowledge
```

```json
[
  { "id": "k-abc123", "topic": "Agent Frameworks", "summary": "Overview of modern agent frameworks" }
]
```

#### `GET /agentican/knowledge/{id}` — get entry with facts

```bash
curl http://localhost:8080/agentican/knowledge/k-abc123
```

```json
{
  "id": "k-abc123",
  "topic": "Agent Frameworks",
  "summary": "Overview of modern agent frameworks",
  "facts": [
    { "id": "f-1", "content": "Most frameworks use tool-calling LLM loops" }
  ]
}
```

#### `POST /agentican/knowledge/{id}/facts` — add fact

```bash
curl -X POST http://localhost:8080/agentican/knowledge/k-abc123/facts \
  -H "content-type: application/json" \
  -d '{"content": "SMAC pattern separates planning from execution"}'
```

#### `DELETE /agentican/knowledge/{id}` — delete entry

```bash
curl -X DELETE http://localhost:8080/agentican/knowledge/k-abc123
```

Returns 204.

### Agents

#### `GET /agentican/agents` — list registered agents

```json
[ { "name": "researcher", "role": "Expert at finding information", "skills": [] } ]
```

#### `GET /agentican/agents/{name}`

Returns the same shape; 404 if unknown.

### WebSocket

Full-duplex alternative to REST + SSE. Connect to `ws://host:port/agentican/ws`.

**Client actions** (JSON messages sent to server):

| Action | Fields | Description |
|---|---|---|
| `submit` | `action: "submit"`, `description` | Submit a task via planner |
| `submit_task` | `action: "submit_task"`, `task`, `inputs` | Submit a pre-built task graph |
| `respond` | `action: "respond"`, `checkpointId`, `approved`, `feedback?` | Respond to a HITL checkpoint |
| `cancel` | `action: "cancel"`, `taskId` | Cancel a running task |
| `subscribe` | `action: "subscribe"`, `taskId` | Subscribe to events for a task |

**Server responses** (JSON messages received from server):

| Type | Fields | Description |
|---|---|---|
| `task_submitted` | `type: "task_submitted"`, `taskId` | Confirms task was accepted |
| `task_started` | `type: "task_started"`, event payload | Task execution began |
| `step_completed` | `type: "step_completed"`, event payload | A step reached terminal state |
| `hitl_checkpoint` | `type: "hitl_checkpoint"`, event payload | Step parked on HITL checkpoint |
| `task_completed` | `type: "task_completed"`, event payload | Task reached terminal state |
| `stream_completed` | `type: "stream_completed"`, `taskId` | No more events for this task |
| `ok` | `type: "ok"` | Action acknowledged (e.g. cancel, respond) |
| `error` | `type: "error"`, `code`, `message` | Error response |

```js
const ws = new WebSocket('ws://localhost:8080/agentican/ws');

ws.onopen = () => {
    ws.send(JSON.stringify({ action: 'submit', description: 'Find papers on agents' }));
};

ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    switch (msg.type) {
        case 'task_submitted': console.log('Task:', msg.taskId); break;
        case 'step_completed': render(msg); break;
        case 'hitl_checkpoint': promptUser(msg); break;
        case 'task_completed': console.log('Done', msg); break;
    }
};
```

## Errors

All 4xx/5xx responses use a structured body:

```json
{ "code": "task_not_found", "message": "No task with id: abc" }
```

| Code | HTTP | When |
|---|---|---|
| `not_found` | 404 | Unknown task / checkpoint / agent |
| `bad_request` | 400 | Missing/invalid request body, conflicting fields |
| `invalid_argument` | 400 | Framework validation failure (e.g. invalid Task graph) |

## OpenAPI / Swagger UI

Auto-generated by `quarkus-smallrye-openapi`. Visit:

- `http://localhost:8080/q/openapi` — OpenAPI 3 spec
- `http://localhost:8080/q/swagger-ui` — interactive UI

## CORS

Browser clients hosted on a different origin need CORS enabled. Minimal config:

```properties
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=https://your-ui.example.com
quarkus.http.cors.methods=GET,POST,DELETE,OPTIONS
quarkus.http.cors.headers=accept,content-type,last-event-id
```

## Authentication

The REST module ships **without** auth. Layer Quarkus Security on top however your app
prefers — JWT, OIDC, basic, etc. Example:

```java
@RolesAllowed("agentican-user")
@Path("/agentican/tasks")
public class TasksResource { ... }
```

Quarkus extensions: `quarkus-smallrye-jwt`, `quarkus-oidc`, `quarkus-elytron-security-properties-file`.

## Lifecycle

The module owns:

- A `TaskService` that tracks in-flight `TaskHandle`s for cancellation
- A `TaskEventBus` that observes lifecycle events from `agentican-quarkus` and fans them
  out via per-task Mutiny `BroadcastProcessor`s

On shutdown, `TaskEventBus` completes every open `BroadcastProcessor` so SSE clients
receive a clean `onComplete` instead of a dropped TCP connection.

## What's not in v2

- **SSE replay / `Last-Event-ID`** — late subscribers don't see history. Use the log
  endpoint for that. (Planned for a future version once persistent task stores land.)
- **List filtering by status / time range** — only `?limit=` for now.
- **Authentication** — bring your own.
- **Multi-tenancy** — single tenant.

