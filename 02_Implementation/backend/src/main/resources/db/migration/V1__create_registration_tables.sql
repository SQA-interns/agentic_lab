CREATE TABLE registration (
    id                UUID         PRIMARY KEY,
    registration_type VARCHAR(16)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    organization      VARCHAR(200),
    study_institution VARCHAR(200),
    study_programme   VARCHAR(200),
    student_id        VARCHAR(50),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registration_type_check CHECK (registration_type IN ('EXTERNAL', 'STUDENT'))
);

CREATE INDEX registration_created_at_idx ON registration (created_at);

CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    category        VARCHAR(16)  NOT NULL,
    PRIMARY KEY (registration_id, option_id),
    CONSTRAINT registration_option_category_check CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER'))
);

CREATE TABLE registration_consent (
    registration_id UUID        NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id      VARCHAR(64) NOT NULL,
    PRIMARY KEY (registration_id, consent_id)
);
