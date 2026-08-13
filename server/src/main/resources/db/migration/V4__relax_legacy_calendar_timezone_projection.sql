-- The normalized temporal table is authoritative for DATE/FLOATING/UTC/ZONED.
-- The legacy weave_calendar_events row remains a compact compatibility projection
-- and therefore must not invent a timezone for DATE or FLOATING values.
ALTER TABLE weave_calendar_events
    ALTER COLUMN timezone_id DROP NOT NULL;
