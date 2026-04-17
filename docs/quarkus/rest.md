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

| Name | Payload | Description |
|---|---|---|
| `task_started` | `{ taskId, taskName }` | Task began |
| `step_completed` | `{ taskId, stepName, status }` | Step reached terminal state |
| `hitl_checkpoint` | `{ taskId, stepName, checkpoint }` | Step parked on HITL |
| `task_completed` | `{ taskId, taskName, status }` | Task reached terminal state |
| `heartbeat` | (comment) | Keep-alive every 30s |

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
