-- V1: initial schema, identical to docs/02_contracts/database.sql (AR-06, ES-08).

CREATE TABLE registration (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name         VARCHAR(100) NOT NULL CHECK (first_name <> ''),
    last_name          VARCHAR(100) NOT NULL CHECK (last_name <> ''),
    email              VARCHAR(254) NOT NULL CHECK (email <> ''),
    -- lower-cased trimmed email; enforces one registration per email (D-15)
    email_normalized   VARCHAR(254) NOT NULL,
    organization       VARCHAR(200),
    study_institution  VARCHAR(200),
    study_programme    VARCHAR(200),
    student_id         VARCHAR(50),
    received_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_registration_email UNIQUE (email_normalized),
    CONSTRAINT ck_registration_type_fields CHECK (
        (type = 'EXTERNAL' AND organization <> ''
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT' AND organization IS NULL
            AND study_institution <> '' AND study_programme <> '' AND student_id <> '')
    )
);

-- Retention job (D-16) and export ordering.
CREATE INDEX ix_registration_received_at ON registration (received_at);

-- Selected options, with the name and category in force when the registration was accepted.
CREATE TABLE registration_option (
    registration_id  UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        VARCHAR(64)  NOT NULL,
    option_name      VARCHAR(200) NOT NULL,
    category         VARCHAR(16)  NOT NULL CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER')),
    PRIMARY KEY (registration_id, option_id)
);

-- Given consents with wording and time (SB-14).
CREATE TABLE registration_consent (
    registration_id  UUID          NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id       VARCHAR(64)   NOT NULL,
    consent_text     VARCHAR(1000) NOT NULL,
    given_at         TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);
