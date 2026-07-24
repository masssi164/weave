create table weave_provider_selections (
    category varchar(80) primary key,
    provider_key varchar(160) not null,
    choice_model varchar(80) not null,
    secret_ref varchar(255),
    selected_by varchar(160) not null,
    selected_at_utc timestamp with time zone not null,
    applied boolean not null,
    support_safe boolean not null,
    migration_dry_run_required boolean not null
);

create table weave_provider_selection_notes (
    category varchar(80) not null,
    note_order integer not null,
    note_text varchar(1024) not null,
    primary key (category, note_order),
    constraint fk_weave_provider_selection_notes
        foreign key (category)
        references weave_provider_selections (category)
        on delete cascade
);
