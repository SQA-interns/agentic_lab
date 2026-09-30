-- Registration storage (docs/02_specification.md section 5).
CREATE TABLE registration (
    id                  uuid         PRIMARY KEY,
    client_request_id   uuid         NOT NULL UNIQUE,
    request_fingerprint varchar(64)  NOT NULL,
    form_type           varchar(16)  NOT NULL CHECK (form_type IN ('external', 'student')),
    first_name          varchar(100) NOT NULL,
    last_name           varchar(100) NOT NULL,
    email               varchar(254) NOT NULL,
    organization        varchar(200),
    study_institution   varchar(200),
    study_programme     varchar(200),
    student_id          varchar(64),
    consent_id          varchar(64),
    consent_given       boolean,
    json_sha256         varchar(64)  NOT NULL,
    accepted_at         timestamptz  NOT NULL,
    CONSTRAINT registration_form_fields CHECK (
        (form_type = 'external' AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR (form_type = 'student' AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL))
);

CREATE INDEX registration_accepted_at_idx ON registration (accepted_at);

CREATE TABLE registration_selection (
    registration_id uuid         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    position        integer      NOT NULL,
    group_id        varchar(16)  NOT NULL CHECK (group_id IN ('workshops', 'events', 'meals', 'other')),
    option_id       varchar(64)  NOT NULL,
    option_name     varchar(200) NOT NULL,
    PRIMARY KEY (registration_id, position),
    UNIQUE (registration_id, group_id, option_id)
);

CREATE TABLE notification_outbox (
    id                bigserial    PRIMARY KEY,
    registration_id   uuid         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    kind              varchar(16)  NOT NULL CHECK (kind IN ('PARTICIPANT', 'ORGANIZER')),
    recipient         varchar(254) NOT NULL,
    subject           varchar(200) NOT NULL,
    body_text         text         NOT NULL,
    attachment_name   varchar(100),
    attachment_sha256 varchar(64),
    status            varchar(16)  NOT NULL CHECK (status IN ('PENDING', 'SENT')),
    attempts          integer      NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz  NOT NULL,
    last_attempt_at   timestamptz,
    sent_at           timestamptz,
    last_error        varchar(200),
    created_at        timestamptz  NOT NULL
);

CREATE INDEX notification_outbox_due_idx ON notification_outbox (next_attempt_at) WHERE status = 'PENDING';
