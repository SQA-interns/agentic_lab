CREATE TABLE registration (
    id                UUID PRIMARY KEY,
    registration_type VARCHAR(16)  NOT NULL CHECK (registration_type IN ('EXTERNAL', 'STUDENT')),
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    organization      VARCHAR(200),
    study_institution VARCHAR(200),
    study_programme   VARCHAR(200),
    student_id        VARCHAR(50),
    privacy_consent   BOOLEAN      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registration_type_fields CHECK (
        (registration_type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (registration_type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL
            AND student_id IS NOT NULL)),
    CONSTRAINT registration_privacy_consent CHECK (privacy_consent)
);

CREATE INDEX registration_created_at_idx ON registration (created_at);

CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    category        VARCHAR(16)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    PRIMARY KEY (registration_id, option_id)
);
