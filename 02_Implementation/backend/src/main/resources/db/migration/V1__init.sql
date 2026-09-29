-- Conference registration schema (specification §3).

create table conference_option (
    id         varchar(64)  primary key,
    category   varchar(16)  not null,
    name       varchar(200) not null,
    active     boolean      not null,
    sort_order integer      not null,
    constraint conference_option_category_chk
        check (category in ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER')),
    constraint conference_option_id_chk
        check (id ~ '^[a-z0-9][a-z0-9_-]{0,63}$')
);

create table registration (
    id                       uuid         primary key,
    type                     varchar(16)  not null,
    first_name               varchar(100) not null,
    last_name                varchar(100) not null,
    email                    varchar(254) not null,
    organization             varchar(200),
    study_institution        varchar(200),
    study_programme          varchar(200),
    student_id               varchar(50),
    personal_data_consent_at timestamptz  not null,
    submitted_at             timestamptz  not null,
    constraint registration_type_chk check (type in ('EXTERNAL', 'STUDENT')),
    constraint registration_type_fields_chk check (
        (type = 'EXTERNAL'
            and organization is not null
            and study_institution is null
            and study_programme is null
            and student_id is null)
        or
        (type = 'STUDENT'
            and organization is null
            and study_institution is not null
            and study_programme is not null
            and student_id is not null)
    )
);

create index registration_submitted_at_idx on registration (submitted_at, id);

create table registration_option (
    registration_id uuid        not null references registration (id) on delete cascade,
    option_id       varchar(64) not null references conference_option (id),
    primary key (registration_id, option_id)
);
