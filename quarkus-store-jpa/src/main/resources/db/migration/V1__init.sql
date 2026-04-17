-- Agentican JPA store — initial schema.
--
-- Four concerns:
--   1) Catalog: agents, skills, tools — reusable across plans.
--   2) Plans: plans (definition_json holds the full Plan graph).
--   3) Task execution: tasks, task_steps, runs, turns, tool_results.
--   4) Knowledge: knowledge_entries, knowledge_facts.
--
-- Plans are persisted as a top-level row plus a JSON serialization of the full
-- Plan graph (params, steps, nested loops/branches, skill/tool refs). This
-- preserves round-trip fidelity without the complexity of relational step
-- decomposition; cross-registry "which plans use agent X" queries are out of
-- scope for this phase.
--
-- Plan identity survives config reloads: plan rows are overwritten when the
-- same plan id is re-registered. For historical fidelity a task carries a
-- plan_snapshot_json column so its plan shape is preserved even if the plan
-- row later changes.

-- ============================================================
-- Catalog
-- ============================================================

-- external_id: stable, human-readable business key used to map config/fluent-declared entries
-- to their DB rows across deploys. Nullable because planner-sourced entries don't carry one.
-- UNIQUE constraint is enforced only when present (partial unique is ANSI-correct: two NULLs
-- compare UNEQUAL so multiple NULL rows are permitted).

CREATE TABLE agents (
    id            VARCHAR(255) PRIMARY KEY,
    external_id   VARCHAR(255) UNIQUE,
    name          VARCHAR(255) NOT NULL,
    role          TEXT,
    llm           VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_agents_name ON agents (name);

CREATE TABLE skills (
    id            VARCHAR(255) PRIMARY KEY,
    external_id   VARCHAR(255) UNIQUE,
    name          VARCHAR(255) NOT NULL,
    instructions  TEXT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_skills_name ON skills (name);

CREATE TABLE tools (
    id               VARCHAR(255) PRIMARY KEY,
    name             VARCHAR(255) NOT NULL UNIQUE,
    description      TEXT,
    properties_json  TEXT,
    required_json    TEXT,
    source           VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL
);

-- ============================================================
-- Plans
-- ============================================================

CREATE TABLE plans (
    id               VARCHAR(255) PRIMARY KEY,
    external_id      VARCHAR(255) UNIQUE,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    definition_json  TEXT         NOT NULL,  -- full Plan graph serialized via Jackson
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_plans_name ON plans (name);

-- ============================================================
-- Task execution
-- ============================================================

CREATE TABLE tasks (
    id                  VARCHAR(255) PRIMARY KEY,
    plan_id             VARCHAR(255) REFERENCES plans (id),   -- nullable for ad-hoc plans
    task_name           VARCHAR(255),
    parent_task_id      VARCHAR(255) REFERENCES tasks (id),
    parent_step_id      VARCHAR(255),                         -- -> task_steps.id; FK added below
    iteration_index     INT          NOT NULL DEFAULT 0,
    status              VARCHAR(32),
    params_json         TEXT,
    plan_snapshot_json  TEXT,                                 -- frozen plan shape at dispatch time
    created_at          TIMESTAMP    NOT NULL,
    completed_at        TIMESTAMP,
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_tasks_plan ON tasks (plan_id);
CREATE INDEX idx_tasks_parent_task ON tasks (parent_task_id);
CREATE INDEX idx_tasks_created_at ON tasks (created_at);

CREATE TABLE task_steps (
    id                           VARCHAR(255) PRIMARY KEY,
    task_id                      VARCHAR(255) NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    plan_step_id                 VARCHAR(255),           -- soft ref into the plan's definition_json
    step_name                    VARCHAR(255),
    status                       VARCHAR(32),
    output                       TEXT,
    aggregate_token_usage_json   TEXT,
    checkpoint_json              TEXT,
    created_at                   TIMESTAMP    NOT NULL,
    completed_at                 TIMESTAMP
);

CREATE INDEX idx_task_steps_task ON task_steps (task_id);

-- tasks.parent_step_id FK added here (requires task_steps to exist)
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_parent_step FOREIGN KEY (parent_step_id) REFERENCES task_steps (id);

CREATE TABLE runs (
    id             VARCHAR(255) PRIMARY KEY,
    task_step_id   VARCHAR(255) NOT NULL REFERENCES task_steps (id) ON DELETE CASCADE,
    agent_id       VARCHAR(255) REFERENCES agents (id),
    agent_name     VARCHAR(255),
    run_index      INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL,
    completed_at   TIMESTAMP
);

CREATE INDEX idx_runs_task_step ON runs (task_step_id);
CREATE INDEX idx_runs_agent ON runs (agent_id);

CREATE TABLE turns (
    id              VARCHAR(255) PRIMARY KEY,
    run_id          VARCHAR(255) NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    turn_index      INT          NOT NULL DEFAULT 0,
    request_json    TEXT,
    response_json   TEXT,
    message_id      VARCHAR(255),
    response_id     VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL,
    completed_at    TIMESTAMP
);

CREATE INDEX idx_turns_run ON turns (run_id);

CREATE TABLE tool_results (
    id              VARCHAR(255) PRIMARY KEY,
    turn_id         VARCHAR(255) NOT NULL REFERENCES turns (id) ON DELETE CASCADE,
    tool_call_id    VARCHAR(255) NOT NULL,
    tool_name       VARCHAR(255) NOT NULL,
    content         TEXT,
    is_error        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_tool_results_turn ON tool_results (turn_id);

-- ============================================================
-- Knowledge
-- ============================================================

CREATE TABLE knowledge_entries (
    id            VARCHAR(255) PRIMARY KEY,
    name          VARCHAR(512) NOT NULL,
    description   TEXT,
    status        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_knowledge_entries_status ON knowledge_entries (status);

CREATE TABLE knowledge_facts (
    id            VARCHAR(255) PRIMARY KEY,
    entry_id      VARCHAR(255) NOT NULL REFERENCES knowledge_entries (id) ON DELETE CASCADE,
    name          VARCHAR(512),
    content       TEXT,
    tags_json     TEXT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_knowledge_facts_entry ON knowledge_facts (entry_id);
