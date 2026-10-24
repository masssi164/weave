-- Preserve existing Chat data while accepting the full canonical Space reference width.
ALTER TABLE weave_chat_conversations ALTER COLUMN context_id TYPE character varying(255);
ALTER TABLE weave_chat_operations ALTER COLUMN context_id TYPE character varying(255);
