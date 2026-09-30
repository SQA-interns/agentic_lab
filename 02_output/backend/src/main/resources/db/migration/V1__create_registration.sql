-- Registration store (specification section 6, ES-08, AR-06).
CREATE TABLE registration (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference               UUID         NOT NULL UNIQUE,
    type                    VARCHAR(16)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name              VARCHAR(200) NOT NULL,
    last_name               VARCHAR(200) NOT NULL,
    email                   VARCHAR(254) NOT NULL,
    organization            VARCHAR(200),
    study_institution       VARCHAR(200),
    study_programme         VARCHAR(200),
    student_id              VARCHAR(200),
    submitted_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    participant_mail_status VARCHAR(16)  NOT NULL,
    organizer_mail_status   VARCHAR(16)  NOT NULL,
    mail_attempts           INTEGER      NOT NULL DEFAULT 0,
    last_mail_attempt_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT registration_fields_by_type CHECK (
        (type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR (type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)
    )
);

CREATE INDEX registration_email_idx ON registration (email);
CREATE INDEX registration_submitted_at_idx ON registration (submitted_at);
CREATE INDEX registration_mail_status_idx
    ON registration (participant_mail_status, organizer_mail_status);

CREATE TABLE registration_option (
    registration_id BIGINT       NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    category        VARCHAR(16)  NOT NULL CHECK (category IN ('WORKSHOP', 'EVENT', 'MEAL', 'OTHER'))
);

CREATE INDEX registration_option_registration_idx ON registration_option (registration_id);

CREATE TABLE registration_consent (
    registration_id BIGINT        NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id      VARCHAR(64)   NOT NULL,
    consent_text    VARCHAR(1000) NOT NULL,
    given_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX registration_consent_registration_idx ON registration_consent (registration_id);
