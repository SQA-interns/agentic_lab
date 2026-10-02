-- Contract "Registration storage" between the backend and PostgreSQL 16 (architecture.md).
-- The first Flyway migration (AR-06) has exactly these statements.

CREATE TABLE registration (
    id                  UUID         PRIMARY KEY,
    type                VARCHAR(8)   NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name          VARCHAR(100) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name           VARCHAR(100) NOT NULL CHECK (btrim(last_name) <> ''),
    email               VARCHAR(254) NOT NULL CHECK (btrim(email) <> ''),
    organization        VARCHAR(200),
    study_institution   VARCHAR(200),
    study_programme     VARCHAR(200),
    student_id          VARCHAR(50),
    consent_id          VARCHAR(64)  NOT NULL,
    consent_text        TEXT         NOT NULL,
    consent_given_at    TIMESTAMPTZ  NOT NULL,
    accepted_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registration_type_fields CHECK (
        (type = 'EXTERNAL'
            AND organization IS NOT NULL AND btrim(organization) <> ''
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT'
            AND organization IS NULL
            AND study_institution IS NOT NULL AND btrim(study_institution) <> ''
            AND study_programme IS NOT NULL AND btrim(study_programme) <> ''
            AND student_id IS NOT NULL AND btrim(student_id) <> '')
    )
);

CREATE INDEX registration_accepted_at_idx ON registration (accepted_at, id);

-- Selected options, with the name and category as they were when the registration was accepted.
CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL CHECK (position >= 0),
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    option_category VARCHAR(8)   NOT NULL CHECK (option_category IN ('workshop', 'event', 'meal', 'other')),
    PRIMARY KEY (registration_id, option_id),
    UNIQUE (registration_id, position)
);
