-- Entity-first repository tests create tables through Hibernate rather than Flyway.
-- Seed the same singleton publication mutex that production owns through V13.
INSERT INTO weave_runner_capability_catalog_locks (lock_id)
VALUES ('public-capability-catalog');
