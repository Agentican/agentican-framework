-- Resume-after-restart columns (see framework docs for the contract).
--
-- * tool_results.state — STARTED / COMPLETED / FAILED. Set when a tool invocation begins so
--   resume can tell "never ran" apart from "was in flight at crash."
-- * turns.state       — STARTED / COMPLETED / ABANDONED. Set when a turn begins so resume can
--   classify an in-flight turn into one of the A..F positions.
-- * task_steps.branch_chosen_path — deterministic path selection persisted after the branch
--   classifier completes, so resume never re-evaluates the classifier.
-- * tasks UNIQUE (parent_task_id, iteration_index) — prevents double-dispatch of the same loop
--   iteration on resume.
--
-- Backfill semantics:
--   * Finished rows at V2 get their correct terminal state (COMPLETED/FAILED based on is_error
--     for tool_results; COMPLETED for turns whose completed_at is populated).
--   * In-flight rows left over from a pre-reaper crash get ABANDONED, not COMPLETED — correctly
--     signalling that they represent work that was interrupted, not work that finished.
--   * After backfill, the default shifts to STARTED for new inserts, since the framework now
--     writes STARTED at the begin-event and updates to COMPLETED at the end-event.

ALTER TABLE tool_results ADD COLUMN state VARCHAR(16);
UPDATE tool_results
   SET state = CASE WHEN is_error = TRUE THEN 'FAILED' ELSE 'COMPLETED' END;
ALTER TABLE tool_results ALTER COLUMN state SET NOT NULL;
ALTER TABLE tool_results ALTER COLUMN state SET DEFAULT 'STARTED';

ALTER TABLE turns ADD COLUMN state VARCHAR(16);
UPDATE turns
   SET state = CASE WHEN completed_at IS NOT NULL THEN 'COMPLETED' ELSE 'ABANDONED' END;
ALTER TABLE turns ALTER COLUMN state SET NOT NULL;
ALTER TABLE turns ALTER COLUMN state SET DEFAULT 'STARTED';

ALTER TABLE task_steps ADD COLUMN branch_chosen_path VARCHAR(255);

-- Multiple NULL parent_task_id rows (top-level tasks) don't collide: NULL is distinct under
-- both PostgreSQL and H2 PostgreSQL-compat unique-index semantics.
CREATE UNIQUE INDEX uq_tasks_parent_iter
    ON tasks (parent_task_id, iteration_index);
