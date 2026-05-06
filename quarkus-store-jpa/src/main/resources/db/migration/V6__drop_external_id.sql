-- Drop external_id from catalog tables.
--
-- After source-mode exclusivity (yaml | builder | database), there is no longer
-- a parallel config source to sync against, so the stable cross-deploy key is
-- gone. The primary key `id` is now the canonical identifier in all modes.
--
-- IF EXISTS guards make this safe in test environments where Hibernate may
-- recreate the schema from entities (without the column) before Flyway runs.

ALTER TABLE agents DROP COLUMN IF EXISTS external_id;
ALTER TABLE skills DROP COLUMN IF EXISTS external_id;
ALTER TABLE plans  DROP COLUMN IF EXISTS external_id;
