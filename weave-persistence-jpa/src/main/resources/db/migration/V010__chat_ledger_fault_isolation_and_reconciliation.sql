alter table weave_chat_appservice_transactions
    add column semantic_fingerprint_version varchar(64) not null default 'matrix-as-semantic-event-set-v2';
alter table weave_chat_appservice_transactions
    add column semantic_mismatch_count integer not null default 0;
alter table weave_chat_appservice_transactions
    add column semantic_mismatch_hash varchar(64);

alter table weave_chat_quarantine
    add column conversation_id varchar(160);
alter table weave_chat_quarantine
    add column category_code varchar(96) not null default 'unclassified';
alter table weave_chat_quarantine
    add column recoverable boolean not null default false;
alter table weave_chat_quarantine
    add column classifier_version varchar(64) not null default 'matrix-synapse-state-v0';
alter table weave_chat_quarantine
    add column lifecycle_state varchar(32) not null default 'rejected';
alter table weave_chat_quarantine
    add column attempt_count integer not null default 0;
alter table weave_chat_quarantine
    add column max_attempts integer not null default 3;
alter table weave_chat_quarantine
    add column last_attempt_at_utc timestamp with time zone;
alter table weave_chat_quarantine
    add column resolved_at_utc timestamp with time zone;
alter table weave_chat_quarantine
    add column last_outcome_code varchar(96);
alter table weave_chat_quarantine
    add column private_homeserver_transaction_id varchar(255);
alter table weave_chat_quarantine
    add column private_provider_event_ref varchar(768);
alter table weave_chat_quarantine
    add column private_provider_room_ref varchar(768);
alter table weave_chat_quarantine
    add column private_normalized_event_json text;

alter table weave_chat_quarantine
    add constraint weave_chat_quarantine_lifecycle_chk
    check (lifecycle_state in ('pending', 'reconciled', 'rejected', 'superseded'));
alter table weave_chat_quarantine
    add constraint weave_chat_quarantine_attempts_chk
    check (attempt_count >= 0 and max_attempts > 0 and attempt_count <= max_attempts);

create index weave_chat_quarantine_scope_idx
    on weave_chat_quarantine (provider_key, tenant_id, conversation_id, lifecycle_state);
