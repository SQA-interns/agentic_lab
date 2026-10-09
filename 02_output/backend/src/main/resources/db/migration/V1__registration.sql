-- Registration storage contract (BR-07, AR-06). PostgreSQL 16.
-- Applied by Flyway as V1__registration.sql; later changes are new migrations only (ES-08).

CREATE TABLE registration (
    id                 uuid         PRIMARY KEY,
    type               varchar(10)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name         varchar(100) NOT NULL CHECK (first_name <> ''),
    last_name          varchar(100) NOT NULL CHECK (last_name <> ''),
    email              varchar(254) NOT NULL CHECK (email <> ''),
    -- lower-cased trimmed email; enforces D-18 under concurrency
    email_normalized   varchar(254) NOT NULL UNIQUE,
    organization       varchar(200),
    study_institution  varchar(200),
    study_programme    varchar(200),
    student_id         varchar(50),
    submitted_at       timestamptz  NOT NULL,
    CONSTRAINT registration_type_fields CHECK (
        (type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)
    )
);

-- Selected options with the name and category as configured at submission time
CREATE TABLE registration_option (
    registration_id  uuid         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        varchar(63)  NOT NULL,
    option_name      varchar(200) NOT NULL,
    category         varchar(10)  NOT NULL CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER')),
    PRIMARY KEY (registration_id, option_id)
);

-- Consents given, with wording and timestamp (SB-14)
CREATE TABLE registration_consent (
    registration_id  uuid          NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id       varchar(63)   NOT NULL,
    consent_text     varchar(2000) NOT NULL,
    given_at         timestamptz   NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);

CREATE INDEX registration_submitted_at_idx ON registration (submitted_at);
