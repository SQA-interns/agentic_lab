-- Registration storage contract (PostgreSQL 16). Shipped unchanged as the first Flyway migration
-- (AR-06, ES-08); later changes are new migrations, never edits of this one.
-- Values are stored trimmed and otherwise exactly as accepted (BR-02, BR-03, NFR-01).

CREATE TABLE registration (
    id                 uuid         PRIMARY KEY,
    type               varchar(16)  NOT NULL CHECK (type IN ('EXTERNAL', 'STUDENT')),
    first_name         varchar(100) NOT NULL CHECK (length(first_name) > 0),
    last_name          varchar(100) NOT NULL CHECK (length(last_name) > 0),
    email              varchar(254) NOT NULL CHECK (length(email) > 0),
    -- lower-cased trimmed email; one registration per address across both types (D-10)
    email_normalized   varchar(254) NOT NULL,
    organization       varchar(200),
    study_institution  varchar(200),
    study_programme    varchar(200),
    student_id         varchar(50),
    consent_id         varchar(64)  NOT NULL,
    consent_text       text         NOT NULL,
    consent_given_at   timestamptz  NOT NULL,
    accepted_at        timestamptz  NOT NULL,
    CONSTRAINT registration_email_normalized_uk UNIQUE (email_normalized),
    CONSTRAINT registration_type_fields_ck CHECK (
        (type = 'EXTERNAL'
            AND organization IS NOT NULL
            AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
        OR
        (type = 'STUDENT'
            AND organization IS NULL
            AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)
    )
);

CREATE INDEX registration_accepted_at_ix ON registration (accepted_at);

-- Selected options with the name and category they had at acceptance; option_id is the stable
-- identifier from the options configuration (AC-003-04).
CREATE TABLE registration_option (
    registration_id  uuid         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id        varchar(64)  NOT NULL,
    option_name      varchar(200) NOT NULL,
    option_category  varchar(16)  NOT NULL CHECK (option_category IN ('workshop', 'event', 'meal', 'other')),
    position         smallint     NOT NULL,
    PRIMARY KEY (registration_id, option_id)
);
