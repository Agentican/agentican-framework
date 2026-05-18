-- Phase 5a: tag every workflow run with the runtime that owns it (IN_PROCESS vs TEMPORAL).
-- AgenticanRecovery skips TEMPORAL rows so it doesn't try to reap or resume tasks whose
-- lifecycle Temporal is already replaying from workflow history. The temporal_workflow_id
-- column lets external callers correlate a framework task back to its Temporal execution.

ALTER TABLE workflow_runs ADD COLUMN runtime VARCHAR(16) NOT NULL DEFAULT 'IN_PROCESS';
ALTER TABLE workflow_runs ADD COLUMN temporal_workflow_id VARCHAR(255);

CREATE INDEX idx_workflow_runs_runtime ON workflow_runs (runtime);
CREATE INDEX idx_workflow_runs_temporal_wf ON workflow_runs (temporal_workflow_id);
