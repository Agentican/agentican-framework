-- Catalog audit: every mutation to an agent / skill / plan (create, update, delete, import)
-- gets a row here. Lets operators answer "when did this change and what did it look like
-- before" without re-reading application logs.
--
-- Actor is null until auth lands — placeholder for when we can attribute changes.

CREATE TABLE catalog_audit (
    id           VARCHAR(64)  PRIMARY KEY,
    entity_type  VARCHAR(32)  NOT NULL,
    entity_ref   VARCHAR(255) NOT NULL,
    action       VARCHAR(32)  NOT NULL,
    actor        VARCHAR(128),
    before_json  TEXT,
    after_json   TEXT,
    created_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_catalog_audit_entity   ON catalog_audit (entity_type, entity_ref, created_at DESC);
CREATE INDEX idx_catalog_audit_created  ON catalog_audit (created_at DESC);
