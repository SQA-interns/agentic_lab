-- AR-03: Flyway owns the schema; Hibernate only validates.
CREATE TABLE registration (
    id                  UUID PRIMARY KEY,
    client_request_id   UUID         NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    participant_type    VARCHAR(16)  NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    organization        VARCHAR(200),
    study_institution   VARCHAR(200),
    study_programme     VARCHAR(200),
    student_id          VARCHAR(64),
    raw_json            TEXT         NOT NULL,
    raw_json_sha256     VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_registration_client_request UNIQUE (client_request_id),
    CONSTRAINT ck_registration_type CHECK (participant_type IN ('EXTERNAL', 'STUDENT')),
    CONSTRAINT ck_registration_fields CHECK (
        (participant_type = 'EXTERNAL' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (participant_type = 'STUDENT' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL
            AND student_id IS NOT NULL))
);

CREATE INDEX ix_registration_created_at ON registration (created_at);

CREATE TABLE registration_selection (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_group    VARCHAR(32)  NOT NULL,
    position        INTEGER      NOT NULL,
    option_id       VARCHAR(64)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    CONSTRAINT uq_selection UNIQUE (registration_id, option_group, option_id)
);

CREATE TABLE registration_consent (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registration_id UUID          NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    consent_id      VARCHAR(64)   NOT NULL,
    consent_text    VARCHAR(2000) NOT NULL,
    CONSTRAINT uq_consent UNIQUE (registration_id, consent_id)
);

-- AR-05: durable email intent; no FK so the notification module is schema-independent.
CREATE TABLE email_outbox (
    id                 UUID PRIMARY KEY,
    registration_id    UUID         NOT NULL,
    kind               VARCHAR(32)  NOT NULL,
    recipient          VARCHAR(254) NOT NULL,
    subject            VARCHAR(300) NOT NULL,
    body               TEXT         NOT NULL,
    attachment_name    VARCHAR(200),
    attachment_content TEXT,
    status             VARCHAR(16)  NOT NULL,
    attempts           INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ  NOT NULL,
    last_error         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL,
    sent_at            TIMESTAMPTZ,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_outbox_kind CHECK (kind IN ('PARTICIPANT_CONFIRMATION', 'ORGANIZER_NOTIFICATION'))
);

CREATE INDEX ix_outbox_due ON email_outbox (status, next_attempt_at);
CREATE INDEX ix_outbox_registration ON email_outbox (registration_id);
