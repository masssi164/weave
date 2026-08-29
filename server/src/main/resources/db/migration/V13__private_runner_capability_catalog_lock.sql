CREATE TABLE weave_runner_capability_catalog_locks (
    lock_id VARCHAR(64) PRIMARY KEY
);

INSERT INTO weave_runner_capability_catalog_locks (lock_id)
VALUES ('public-capability-catalog');
