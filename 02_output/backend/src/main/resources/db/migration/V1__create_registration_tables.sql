-- Registration storage (PostgreSQL 16). The backend's first Flyway migration
-- (V1__create_registration_tables.sql) is exactly this file (AR-06, ES-08).

CREATE TABLE registration (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(8)   NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    -- lower-cased email; one registration per address across both types (D-13)
    email_normalized   VARCHAR(254) NOT NULL,
    organization       VARCHAR(200),
    study_institution  VARCHAR(200),
    study_programme    VARCHAR(200),
    student_id         VARCHAR(50),
    registered_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_registration_email UNIQUE (email_normalized),
    CONSTRAINT ck_registration_type_fields CHECK (
        (type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)
    )
);

-- Selected options with the name and category they had when the registration was accepted,
-- so a later configuration change does not alter stored registrations.
CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    category        VARCHAR(16)  NOT NULL CHECK (category IN ('workshop', 'event', 'meal', 'other')),
    PRIMARY KEY (registration_id, option_id)
);

-- Consents given, with the wording shown and the time given (SB-14).
CREATE TABLE registration_consent (
    registration_id UUID        NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id      VARCHAR(64) NOT NULL,
    consent_text    TEXT        NOT NULL,
    given_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);

CREATE INDEX ix_registration_registered_at ON registration (registered_at);
