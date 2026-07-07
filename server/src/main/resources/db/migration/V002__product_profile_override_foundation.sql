create table weave_product_profile_overrides (
    primary_identity_key varchar(255) primary key,
    display_name varchar(255),
    avatar varchar(512),
    locale varchar(40),
    timezone varchar(80),
    accessibility_preferences_json clob not null,
    profile_visibility varchar(80)
);
