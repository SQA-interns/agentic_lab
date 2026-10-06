-- Registration storage contract (PostgreSQL 16). Applied as Flyway migration V1 (AR-06, ES-08).
-- BR-01 fixed fields per type, BR-07 storage, D-15 one registration per email, SB-14 consent with time.

CREATE TABLE registration (
    id                 UUID         PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name         VARCHAR(100) NOT NULL CHECK (length(btrim(first_name)) > 0),
    last_name          VARCHAR(100) NOT NULL CHECK (length(btrim(last_name)) > 0),
    email              VARCHAR(254) NOT NULL CHECK (length(btrim(email)) > 0),
    email_normalized   VARCHAR(254) NOT NULL,
    organization       VARCHAR(200),
    study_institution  VARCHAR(200),
    study_programme    VARCHAR(200),
    student_id         VARCHAR(50),
    registered_at      TIMESTAMPTZ  NOT NULL,
    json_copy_file     VARCHAR(255) NOT NULL,
    CONSTRAINT registration_email_unique UNIQUE (email_normalized),
    CONSTRAINT registration_external_fields CHECK (
        type <> 'EXTERNAL' OR (
            organization IS NOT NULL AND length(btrim(organization)) > 0
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)),
    CONSTRAINT registration_student_fields CHECK (
        type <> 'STUDENT' OR (
            organization IS NULL
            AND study_institution IS NOT NULL AND length(btrim(study_institution)) > 0
            AND study_programme IS NOT NULL AND length(btrim(study_programme)) > 0
            AND student_id IS NOT NULL AND length(btrim(student_id)) > 0))
);

CREATE INDEX registration_registered_at_idx ON registration (registered_at);

-- Selected options with the display name and category valid at registration time (AR-04: options may change later).
CREATE TABLE registration_option (
    registration_id  UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        VARCHAR(64)  NOT NULL,
    display_name     VARCHAR(200) NOT NULL,
    category         VARCHAR(16)  NOT NULL CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER')),
    position         INTEGER      NOT NULL,
    PRIMARY KEY (registration_id, option_id)
);

-- Consents given, with the wording shown and the time (BR-05, SB-14).
CREATE TABLE registration_consent (
    registration_id  UUID          NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id       VARCHAR(64)   NOT NULL,
    consent_text     VARCHAR(1000) NOT NULL,
    given_at         TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);
