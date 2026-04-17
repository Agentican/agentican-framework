-- HITL response persistence: when a human responds to an approval/question checkpoint, persist
-- the response JSON so that a crash between response and step-advance doesn't lose it. On
-- resume, the rehydrated checkpoint's pending future is immediately completed with the
-- persisted response — no re-approval needed from the user.

ALTER TABLE task_steps ADD COLUMN hitl_response_json TEXT;
