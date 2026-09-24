CREATE TABLE registration (
    id                UUID         PRIMARY KEY,
    type              VARCHAR(16)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    organization      VARCHAR(200),
    study_institution VARCHAR(200),
    study_programme   VARCHAR(200),
    student_id        VARCHAR(50),
    privacy_consent   BOOLEAN      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    backup_file       VARCHAR(255) NOT NULL,
    CONSTRAINT registration_type_chk CHECK (type IN ('EXTERNAL', 'STUDENT')),
    CONSTRAINT registration_consent_chk CHECK (privacy_consent),
    CONSTRAINT registration_external_fields_chk CHECK (
        type <> 'EXTERNAL'
        OR (organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)),
    CONSTRAINT registration_student_fields_chk CHECK (
        type <> 'STUDENT'
        OR (organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL))
);

CREATE INDEX registration_created_at_idx ON registration (created_at);

CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    category        VARCHAR(16)  NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    PRIMARY KEY (registration_id, option_id),
    CONSTRAINT registration_option_category_chk CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'ACTIVITY'))
);
