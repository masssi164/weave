create table weave_product_profile_overrides (
    primary_identity_key varchar(528) primary key,
    display_name varchar(255),
    avatar varchar(512),
    locale varchar(40),
    timezone varchar(80),
    accessibility_preferences_json text not null,
    profile_visibility varchar(80)
);
