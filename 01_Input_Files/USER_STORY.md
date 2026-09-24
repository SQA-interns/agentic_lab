# Conference Registration System — User Stories

This file defines business intent only. It must not name a programming
language, framework, database, or any other technology choice — those
belong in `TECH_STACK.md`. If a technology decision appears to be implied
here, treat `TECH_STACK.md` as authoritative.

Each story includes an explicit scope boundary so acceptance criteria
cannot silently expand or shrink coverage. See `ACCEPTANCE_CRITERIA_RULES.md`
for how these stories must be turned into acceptance criteria.

---

## Epic

As a conference organizer, I want participants to register for the
conference through an online registration system, so that registrations
can be collected, processed, stored and managed reliably.

---

## US-001 — External participant registration

As an external conference participant, I want to register for the
conference using the external participant registration form, so that I
can attend the conference and selected conference activities.

**In scope:** the external registration form and its submission path.
**Out of scope:** payment processing, account creation, editing a
submitted registration.

---

## US-002 — Student registration

As a student, I want to register for the conference using the student
registration form, so that I can attend the conference and activities
available to students.

**In scope:** the student registration form and its submission path.
**Out of scope:** verification of student status beyond the fields
collected on the form.

---

## US-003 — Configurable conference options

As a conference organizer, I want to change the available workshops and
optional conference activities without changing the fixed
participant-data fields, so that the same registration system can be
reused when the conference programme changes.

**In scope:** activating/deactivating and defining configurable options
(workshops, events, meals, other checkbox-based activities).
**Out of scope:** an administrative UI for editing options — a
configuration file or equivalent mechanism is sufficient unless
`PROJECT_CONSTRAINTS.md` says otherwise.

---

## US-004 — Registration confirmation in the application

As a participant, I want to receive clear confirmation after my
registration has been successfully processed, so that I know that my
registration was received.

**In scope:** in-application confirmation shown only after a successful
backend response.
**Out of scope:** the confirmation email itself (see US-006).

---

## US-005 — Reliable registration storage

As a conference organizer, I want every successfully submitted
registration to be stored reliably and backed up, so that participant
registrations are not lost and can be recovered if necessary.

**In scope:** relational persistence plus a JSON backup on persistent
storage, per successful registration.
**Out of scope:** long-term archival policy, data retention limits.

---

## US-006 — Participant email confirmation

As a registered participant, I want to receive an email confirming my
registration, so that I have a record of my successful registration.

**In scope:** a confirmation email sent after successful processing.
**Out of scope:** email template customization by organizers.

---

## US-007 — Organizer notification

As a conference organizer, I want to be notified when a new participant
registers and receive the submitted registration information, so that I
have an additional record of every registration and can follow
registrations as they arrive.

**In scope:** a notification email to organizers per registration,
including the registration JSON as an attachment.
**Out of scope:** a live dashboard or real-time feed.

---

## US-008 — Excel export

As a conference organizer, I want to export the current list of
registered participants to an Excel file at any time, so that I can
process, review and share registration data outside the registration
system.

**In scope:** an on-demand export of current registrations to Excel
format.
**Out of scope:** scheduled/automatic export delivery.
