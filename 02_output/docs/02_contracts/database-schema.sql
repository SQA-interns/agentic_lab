-- Registration storage contract (backend <-> PostgreSQL 16).
-- Created only by Flyway migrations (AR-06, ES-08); migration V1 must produce exactly this schema.
-- Personal data: every column of registration except id and submitted_at, and the consent rows (SB-12, SB-13; retention D-11).

CREATE TABLE registration (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(10)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    submitted_at       TIMESTAMPTZ  NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    email              VARCHAR(254) NOT NULL,
    organization       VARCHAR(200),
    study_institution  VARCHAR(200),
    study_programme    VARCHAR(200),
    student_id         VARCHAR(50),
    CONSTRAINT registration_fields_by_type CHECK (
        (type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)
    )
);

-- One registration per email address, case-insensitive (D-10, AC-001-09).
CREATE UNIQUE INDEX registration_email_unique ON registration (lower(email));

-- Selected options: id plus the name and category shown at registration time,
-- so the export stays correct after the configuration changes.
CREATE TABLE registration_option (
    registration_id  UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        VARCHAR(64)  NOT NULL,
    option_name      VARCHAR(200) NOT NULL,
    category         VARCHAR(10)  NOT NULL CHECK (category IN ('workshop', 'event', 'meal', 'other')),
    position         INTEGER      NOT NULL,
    PRIMARY KEY (registration_id, option_id)
);

-- Consents given, with the time they were given (SB-14, AC-005-01).
CREATE TABLE registration_consent (
    registration_id  UUID        NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id       VARCHAR(64) NOT NULL,
    given_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);
