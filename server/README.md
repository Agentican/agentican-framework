# Agentican Server

A Quarkus application that bundles every `agentican-quarkus` module — runtime, REST, metrics, OpenTelemetry, scheduler, and both JPA stores — into a single runnable server with a Playground web UI.

## Status

**Playground today.** Wire up an API key, boot it, and explore the framework through a browser: submit tasks, watch them run, browse agents/skills/tools, inspect the knowledge base, see live traces.

**Production server tomorrow.** The intent is to grow this into a hardened, deployable Agentican server — auth, multi-tenant configuration, container images, and the operational concerns a real deployment needs. Treat the current shape as provisional; it will change as the server matures.

## Running

Requires Java 25 (preview features enabled), Docker (for Dev-Services Postgres), and an Anthropic API key.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
# optional, for Composio toolkits
export COMPOSIO_API_KEY=...
export COMPOSIO_USER_ID=...

# one-time: let Testcontainers keep the Postgres container alive across restarts
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties

../mvnw quarkus:dev -pl agentican-server
```

The server listens on `http://localhost:9000`. Open that in a browser for the Playground; the Quarkus Dev UI is at `http://localhost:9000/q/dev/`.

## Playground Web UI

A single-page vanilla JS app served from `src/main/resources/META-INF/resources/`. The URL hash selects the active panel, so refresh and browser back/forward preserve state (`/#knowledge`, `/#plans`, etc.).

### Tasks (`/#tasks`)
- Form to submit a free-form task or a pre-built plan by id.
- **Recent Tasks** table with live updates — the Tasks panel subscribes to the task event stream (`SSE`) so rows populate as work progresses.
- Click a row to open **Task Detail** with five tabs:
  - **Events** — chronological stream of plan/task/step/run/turn/tool lifecycle events.
  - **Plan** — the plan definition as a DAG (topologically layered, same renderer as the Plans panel). Purely static — progress lives in Events and Trace.
  - **Trace** — OpenTelemetry waterfall pulled from `/agentican/traces/{taskId}`.
  - **Metrics** — per-task token usage, cache hits, duration, step/run/turn counts.
  - **Result** — the final step's output.
- Turn-level detail opens a modal with the full LLM request and response.

### Plans (`/#plans`)
Registered plans from the `PlanRegistry`, sorted by name. Each card expands to reveal the plan DAG with per-step agent, skills, tools, dependencies, and condition metadata. Step cards expand to show full instructions.

### Agents (`/#agents`)
Registered agents from the `AgentRegistry`, sorted by name. Cards expand to show id and role.

### Skills (`/#skills`)
Registered skills from the `SkillRegistry`, sorted by name. Cards expand to show id and the full instructions snippet.

### Tools (`/#tools`)
Registered toolkits (MCP, Composio, custom) sorted by display name. Each toolkit expands into its tools (also sorted), and each tool expands to show its description.

### Knowledge (`/#knowledge`)
Read-only view of the knowledge base. Cards show entry name + fact count; click opens a modal with the full description, all facts (name, content, tags), and scrolls when long. Writes happen entirely through agents — the UI does not create or delete entries.

### Metrics (`/#metrics`)
Live Prometheus metrics scraped from `/q/metrics`, filtered to the `agentican_*` family and sorted alphabetically. Manual refresh button.

### Config (`/#config`)
Current runtime configuration properties, sorted by key.

### Theme
Light/dark toggle in the sidebar footer. Choice persists via `localStorage`.

## Persistence

Everything survives restarts. Agents, skills, plans, tasks, steps, runs, turns, knowledge entries, and OpenTelemetry spans all persist to Postgres:

- **Dev-Services** provisions the Postgres container automatically (no URL/user/password config).
- **`quarkus.datasource.devservices.reuse=true`** tells Quarkus not to stop the container on shutdown.
- **`testcontainers.reuse.enable=true`** in `~/.testcontainers.properties` makes Testcontainers itself willing to reuse containers across JVM runs. Without this flag the container (and all your data) dies when `quarkus:dev` exits.
- Flyway runs both schema migrations on boot: `V1__init.sql` from `agentican-quarkus-store-jpa` (catalog + task state + knowledge), `V2__spans.sql` from `agentican-quarkus-otel-store-jpa`.

To wipe state:
```bash
docker ps   # find the reusable Testcontainers postgres container
docker rm -f <container-id>
```

### Task resume on restart

Tasks that were RUNNING when the server died are automatically resumed on next startup. Completed steps are skipped; in-flight agent steps resume at turn-boundary granularity (no redundant LLM calls; already-completed tool calls are not re-run); loop iterations resume child-by-child; branch steps respect the previously chosen path; pending HITL checkpoints are rehydrated so the Approve/Reject buttons still work.

**One caveat:** a tool call that was in-flight at the exact crash moment may run twice on resume. We can't distinguish "tool was about to run" from "tool ran but completion wasn't persisted." Tool authors whose side effects aren't idempotent should design for at-most-once invocation per crash.

See `agentican-quarkus-store-jpa/README.md` for the full resume contract. Disable via `agentican.resume-on-start=false`.

## Configuration

Defaults live in `src/main/resources/application.properties`:

- One default LLM (`claude-sonnet-4-5`) bound to `$ANTHROPIC_API_KEY`.
- Composio toolkits bound to `$COMPOSIO_API_KEY` / `$COMPOSIO_USER_ID` when present.
- CORS wide-open (useful for playground experimentation; tighten for production).
- OTel enabled; 1s span batch flush for responsive live traces.
- Verbose logging on `LlmClient`, `SmacAgentRunner`, and `PlannerAgent` for diagnosing recall loops, tool-argument drift, and planner behavior. Flip to `INFO` when no longer needed.
- `quarkus.index-dependency.*` entries register each Agentican module with Jandex so beans, config mappers, and CDI producers are discovered.

Override any of it via environment variables or your own `application.properties`.

### Adding agents, skills, or plans via config

Config-declared agents and skills require a stable `external-id` — the business key that maps the logical item to its DB catalog row across deploys. Without it, the server refuses to boot.

```properties
agentican.agents[0].external-id=researcher
agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding information

agentican.skills[0].external-id=source-triangulation
agentican.skills[0].name=Source Triangulation
agentican.skills[0].instructions=Cross-verify claims against ≥3 independent primary sources.
```

Planner-generated agents and skills legitimately have no `external-id` — only user-declared entries require it.

## REST API

The REST module exposes `/agentican/*` endpoints for tasks, plans, agents, skills, tools, knowledge, traces, config, and health. See the `agentican-quarkus-rest` README for details. The Playground is built entirely on these endpoints.
