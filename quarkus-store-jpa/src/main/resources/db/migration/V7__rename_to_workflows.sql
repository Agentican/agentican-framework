-- Vocabulary unification: Plan/Task domain words become Workflow/WorkflowRun.
--   plans       -> workflows
--   tasks       -> workflow_runs
--   task_steps  -> workflow_run_steps
--   runs        -> agent_runs           (disambiguated from workflow_runs)
-- Indexes and FKs are renamed to match.

ALTER TABLE plans       RENAME TO workflows;
ALTER TABLE tasks       RENAME TO workflow_runs;
ALTER TABLE task_steps  RENAME TO workflow_run_steps;
ALTER TABLE runs        RENAME TO agent_runs;

-- Foreign-key column renames
ALTER TABLE workflow_runs       RENAME COLUMN plan_id TO workflow_id;
ALTER TABLE workflow_runs       RENAME COLUMN parent_task_id TO parent_workflow_run_id;
ALTER TABLE workflow_runs       RENAME COLUMN task_name TO workflow_run_name;
ALTER TABLE workflow_runs       RENAME COLUMN plan_snapshot_json TO workflow_snapshot_json;
ALTER TABLE workflow_run_steps  RENAME COLUMN task_id TO workflow_run_id;
ALTER TABLE workflow_run_steps  RENAME COLUMN plan_step_id TO workflow_step_id;
ALTER TABLE agent_runs          RENAME COLUMN task_step_id TO workflow_run_step_id;

-- Index renames
ALTER INDEX idx_plans_name           RENAME TO idx_workflows_name;
ALTER INDEX idx_tasks_plan           RENAME TO idx_workflow_runs_workflow;
ALTER INDEX idx_tasks_parent_task    RENAME TO idx_workflow_runs_parent;
ALTER INDEX idx_tasks_created_at     RENAME TO idx_workflow_runs_created_at;
ALTER INDEX idx_task_steps_task      RENAME TO idx_workflow_run_steps_run;
ALTER INDEX idx_runs_task_step       RENAME TO idx_agent_runs_run_step;
ALTER INDEX idx_runs_agent           RENAME TO idx_agent_runs_agent;
