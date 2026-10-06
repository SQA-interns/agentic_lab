-- Registration storage contract (backend <-> PostgreSQL). Applied as Flyway V1 (AR-06, ES-08).
-- Option name and category are copied at acceptance so the export stays correct after the
-- option configuration changes (US-003).

CREATE TABLE registration (
    id                 uuid         PRIMARY KEY,
    registration_type  varchar(16)  NOT NULL CHECK (registration_type IN ('EXTERNAL', 'STUDENT')),
    first_name         varchar(100) NOT NULL CHECK (first_name <> ''),
    last_name          varchar(100) NOT NULL CHECK (last_name <> ''),
    email              varchar(254) NOT NULL CHECK (email <> ''),
    email_normalized   varchar(254) NOT NULL,
    organization       varchar(200),
    study_institution  varchar(200),
    study_programme    varchar(200),
    student_id         varchar(50),
    consent_id         varchar(64)  NOT NULL,
    consent_text       text         NOT NULL,
    consent_given_at   timestamptz  NOT NULL,
    received_at        timestamptz  NOT NULL,
    json_copy_file     varchar(255) NOT NULL,
    CONSTRAINT uq_registration_email UNIQUE (email_normalized),
    CONSTRAINT ck_registration_type_fields CHECK (
        (registration_type = 'EXTERNAL'
            AND organization IS NOT NULL AND organization <> ''
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (registration_type = 'STUDENT'
            AND organization IS NULL
            AND study_institution IS NOT NULL AND study_institution <> ''
            AND study_programme IS NOT NULL AND study_programme <> ''
            AND student_id IS NOT NULL AND student_id <> '')
    )
);

CREATE INDEX ix_registration_received_at ON registration (received_at);

CREATE TABLE registration_option (
    registration_id  uuid         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        varchar(64)  NOT NULL,
    option_name      varchar(200) NOT NULL,
    option_category  varchar(16)  NOT NULL CHECK (option_category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER')),
    PRIMARY KEY (registration_id, option_id)
);
