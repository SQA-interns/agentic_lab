-- Registration storage contract (PostgreSQL 16). The backend's Flyway migration
-- V1__registration_schema.sql is a byte-identical copy of this file (AR-06, ES-08).

CREATE TABLE registration (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('external', 'student')),
    first_name         VARCHAR(100) NOT NULL CHECK (first_name <> ''),
    last_name          VARCHAR(100) NOT NULL CHECK (last_name <> ''),
    email              VARCHAR(254) NOT NULL CHECK (email <> ''),
    email_normalized   VARCHAR(254) NOT NULL,
    organization       VARCHAR(200),
    study_institution  VARCHAR(200),
    study_programme    VARCHAR(200),
    student_id         VARCHAR(50),
    received_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registration_email_unique UNIQUE (email_normalized),
    CONSTRAINT registration_type_fields CHECK (
        (type = 'external'
            AND organization IS NOT NULL AND organization <> ''
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'student'
            AND organization IS NULL
            AND study_institution IS NOT NULL AND study_institution <> ''
            AND study_programme IS NOT NULL AND study_programme <> ''
            AND student_id IS NOT NULL AND student_id <> '')
    )
);

CREATE INDEX registration_received_at_idx ON registration (received_at);

-- Selected options, with name and category as shown when the registration was accepted
CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    category        VARCHAR(16)  NOT NULL CHECK (category IN ('workshop', 'event', 'meal', 'other')),
    PRIMARY KEY (registration_id, option_id)
);

-- Consents given, with their wording and timestamp (SB-14)
CREATE TABLE registration_consent (
    registration_id UUID          NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id      VARCHAR(64)   NOT NULL,
    consent_text    VARCHAR(2000) NOT NULL,
    given_at        TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);
