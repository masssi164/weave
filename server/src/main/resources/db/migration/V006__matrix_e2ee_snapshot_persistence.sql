create table weave_matrix_e2ee_snapshots (
    tenant_id varchar(160) primary key,
    sequence_value bigint not null,
    payload_json text not null,
    updated_at_utc timestamp with time zone not null
);
