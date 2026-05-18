# REST & Real-Time

Requires `agentican-quarkus-rest`. Adds REST endpoints, SSE streaming, WebSocket, and the
HITL bridge. OpenAPI spec auto-generated at `/q/openapi`, browseable at `/q/swagger-ui`.

## REST endpoints

All mounted under `/agentican`. Override with `quarkus.http.root-path`.

### Tasks

| Method | Path | Description |
|---|---|---|
| `POST` | `/agentican/tasks` | Submit task → 201 + `{ taskId }` |
| `GET` | `/agentican/tasks` | List tasks (`?limit=`, `?status=`, `?since=`) |
| `GET` | `/agentican/tasks/{id}` | Task summary |
| `GET` | `/agentican/tasks/{id}/log` | Full log with per-step run data |
| `GET` | `/agentican/tasks/{id}/steps/{stepName}/runs/{runIndex}/turns/{turnIndex}` | Inspect a single turn — useful for granular debugging |
| `GET` | `/agentican/tasks/{id}/stream` | SSE event stream |
| `DELETE` | `/agentican/tasks/{id}` | Cancel (cooperative) |

**Submit — planner mode:**

```bash
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{"description": "Find papers on agent frameworks"}'
```

**Submit — pre-built task:**

```bash
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{
    "task": {
      "name": "research",
      "description": "Research X",
      "steps": [{
        "type": "agent",
        "name": "search",
        "agentName": "researcher",
        "instructions": "Find papers about ${topic}"
      }]
    },
    "inputs": { "topic": "agents" }
  }'
```

Step types: `"agent"`, `"loop"`, `"branch"` — polymorphic via `@JsonTypeInfo`.

### Checkpoints (HITL bridge)

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/checkpoints` | All pending checkpoints |
| `GET` | `/agentican/checkpoints/{taskId}` | Checkpoints for one task |
| `POST` | `/agentican/checkpoints/{id}/respond` | Approve/reject → 204 |
| `POST` | `/agentican/checkpoints/{id}/cancel` | Cancel checkpoint → 204 |

**Approve:**

```bash
curl -X POST http://localhost:8080/agentican/checkpoints/{id}/respond \
  -H "content-type: application/json" \
  -d '{"approved": true, "feedback": "Ship it"}'
```

The parked virtual thread wakes up immediately — no polling, no state machine.

### Knowledge

| Method | Path | Description |
|---|---|---|
| `POST` | `/agentican/knowledge` | Create entry → 201 |
| `GET` | `/agentican/knowledge` | List entries |
| `GET` | `/agentican/knowledge/{id}` | Entry with facts |
| `POST` | `/agentican/knowledge/{id}/facts` | Add fact |
| `DELETE` | `/agentican/knowledge/{id}` | Delete entry → 204 |

### Agents

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/agents` | List registered agents |
| `GET` | `/agentican/agents/{name}` | Agent detail |
| `POST` | `/agentican/agents` | Register / update an agent at runtime (JSON body) |
| `PUT` | `/agentican/agents/{ref}` | Update an existing agent |
| `DELETE` | `/agentican/agents/{ref}` | Remove an agent |

### Skills

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/skills` | List registered skills |
| `GET` | `/agentican/skills/{ref}` | Skill detail |
| `POST` | `/agentican/skills` | Create a skill |
| `PUT` | `/agentican/skills/{ref}` | Update a skill |
| `DELETE` | `/agentican/skills/{ref}` | Remove a skill |

### Workflows (aka plans)

`/agentican/plans` is the canonical path; treat the `plans` ↔ `workflows` distinction as
interchangeable (both terms appear in catalog/code for historical reasons).

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/plans` | List registered workflow definitions |
| `GET` | `/agentican/plans/{ref}` | Workflow detail |
| `POST` | `/agentican/plans` | Register a workflow (JSON or YAML body via `Content-Type`) |
| `PUT` | `/agentican/plans/{ref}` | Update a workflow |
| `DELETE` | `/agentican/plans/{ref}` | Remove a workflow |

### Tools

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/tools` | List registered toolkits and the tools they expose (slug, display name, description). Useful for UIs that need to render the agent's tool surface. |

### Config

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/config` | Read the application's effective Agentican-related Quarkus config properties (OTel endpoint, HTTP port, CORS, etc.) |
| `GET` | `/agentican/config/export` | Export the live catalog (agents + skills + workflows) as JSON |
| `GET` | `/agentican/config/export.yaml` | Same as above, but in catalog YAML format suitable for committing |
| `POST` | `/agentican/config/import` | Import a catalog YAML/JSON document; merges into the registries. Useful for multi-env catalog migration. |

### Audit

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/audit` | Catalog-mutation audit trail. Filters: `entityType`, `entityRef`, `since` (ISO timestamp), `limit`. |
| `DELETE` | `/agentican/audit` | Prune audit entries older than `before` (ISO timestamp). |

### Traces

Available only when `agentican-quarkus-otel` is on the classpath.

| Method | Path | Description |
|---|---|---|
| `GET` | `/agentican/traces/{taskId}` | Returns the OTel `SpanView` list for a given task. By default spans live in an in-memory LRU of 100 traces; add `agentican-quarkus-otel-store-jpa` for Postgres-backed persistence. |

## SSE streaming

Subscribe to real-time events for a task:

```bash
curl -N http://localhost:8080/agentican/tasks/{taskId}/stream
```

```js
const es = new EventSource('/agentican/tasks/abc/stream');

es.addEventListener('task_started', e => console.log('started'));
es.addEventListener('step_completed', e => render(JSON.parse(e.data)));
es.addEventListener('hitl_checkpoint', e => promptUser(JSON.parse(e.data)));
es.addEventListener('task_completed', e => { es.close(); });
```

### Event types

The SSE channel emits one event per framework lifecycle moment. The full set, in
the order they typically appear within a task, is below — clients can ignore any
event they don't care about, but should not be surprised by them. (Source of truth:
[`SseEventTypes.java`](../../quarkus-rest/src/main/java/ai/agentican/quarkus/rest/sse/SseEventTypes.java).)

| Name | Payload shape | Fires when |
|---|---|---|
| `plan_started` | `{ taskId, taskDescription }` | Planning began (only when the workflow planner is in play) |
| `plan_completed` | `{ taskId, taskName, planId }` | Plan resolved; execution about to begin |
| `task_started` | `{ taskId, taskName, parentTaskId }` | Task entered the executor |
| `step_started` | `{ stepId, taskId, stepName }` | Step began |
| `step_completed` | `{ stepId, taskId, stepName, status }` | Step reached a terminal status |
| `run_started` | `{ runId, stepId, agentName, runIndex, taskId }` | An agent run started within a step (resume → multiple runs per step) |
| `run_completed` | `{ runId, stepId, agentName, runIndex, taskId }` | Run finished |
| `turn_started` | `{ turnId, runId, agentName, turn, taskId }` | One LLM round-trip began within a run |
| `turn_completed` | `{ turnId, runId, agentName, turn, taskId }` | Turn finished |
| `message_sent` | `{ messageId, turnId, agentName, turn, taskId }` | LLM request dispatched |
| `response_received` | `{ responseId, turnId, agentName, turn, stopReason, inputTokens, outputTokens, toolCallCount, taskId }` | LLM responded |
| `tool_call_started` | `{ toolCallId, turnId, toolName, taskId }` | Tool invocation began |
| `tool_call_completed` | `{ toolCallId, turnId, toolName, isError, taskId }` | Tool invocation finished |
| `hitl_checkpoint` | `{ taskId, stepId, stepName, checkpoint }` | Step parked awaiting a human response |
| `iteration_started` | `{ taskId, parentStepId, parentTaskId, taskName, iterationIndex }` | A loop-body sub-task began |
| `iteration_completed` | `{ taskId, parentStepId, parentTaskId, status }` | Loop-body sub-task finished |
| `task_completed` | `{ taskId, taskName, status }` | Task reached a terminal status |
| `heartbeat` | (SSE comment) | Keep-alive every 30s |

The unknown / fallback name is `event` — emitted only if a future framework event lacks
an explicit mapping. Clients that switch on event name should treat it as ignorable.

### Replay with `Last-Event-ID`

Each SSE event has a monotonic `id`. On reconnect, the browser sends `Last-Event-ID`
automatically. The server replays missed events from a per-task buffer (100 events).

```
EventSource reconnects → sends Last-Event-ID: 5 → receives events 6, 7, 8... → seamless
```

Manual fallback: `GET /tasks/{id}/stream?lastEventId=5`.

## WebSocket

Full-duplex alternative to REST + SSE. Connect to `ws://host:port/agentican/ws`.

```js
const ws = new WebSocket('ws://localhost:8080/agentican/ws');

ws.onopen = () => {
    // Submit a task
    ws.send(JSON.stringify({ action: 'submit', description: 'Find papers on agents' }));
};

ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.type === 'task_submitted') {
        // Subscribe to events
        ws.send(JSON.stringify({ action: 'subscribe', taskId: msg.data.taskId }));
    }
    if (msg.type === 'hitl_checkpoint') {
        // Approve over the same connection
        ws.send(JSON.stringify({
            action: 'respond', checkpointId: msg.data.checkpointId, approved: true
        }));
    }
};
```

### Client actions

| Action | Required fields | Description |
|---|---|---|
| `submit` | `description` | Submit via planner |
| `submit_task` | `task` | Submit pre-built task (optional `inputs`) |
| `respond` | `checkpointId`, `approved` | Respond to HITL (optional `feedback`) |
| `cancel` | `taskId` | Cancel running task |
| `subscribe` | `taskId` | Subscribe to events for a task |

### Server responses

| Type | Description |
|---|---|
| `task_submitted` | Task accepted, includes `taskId` |
| `task_started` / `step_completed` / `hitl_checkpoint` / `task_completed` | Lifecycle events |
| `stream_completed` | No more events for the subscribed task |
| `ok` | Action acknowledged |
| `error` | Error with message |

## Structured errors

All 4xx/5xx responses return:

```json
{ "code": "not_found", "message": "No task with id: abc" }
```

| Code | HTTP | When |
|---|---|---|
| `not_found` | 404 | Unknown task / checkpoint / agent / knowledge entry |
| `bad_request` | 400 | Missing/invalid request body |
| `invalid_argument` | 400 | Framework validation failure |

## CORS

```properties
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=https://your-ui.example.com
quarkus.http.cors.methods=GET,POST,DELETE,OPTIONS
quarkus.http.cors.headers=accept,content-type,last-event-id
```
