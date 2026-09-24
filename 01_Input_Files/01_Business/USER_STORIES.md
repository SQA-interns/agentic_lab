# Conference Registration System — User Stories

This file defines business intent only.

It must not define:
- programming languages;
- frameworks;
- databases;
- deployment technologies;
- API implementation;
- storage implementation.

Those belong to the technical input files.

---

## Epic

As a conference organizer,
I want participants to register for the conference through an online
registration system,
so that registrations can be collected and managed reliably.

---

## US-001 — External participant registration

As an external conference participant,
I want to register for the conference using the external participant
registration form,
so that I can attend the conference and selected conference activities.

In scope:
- external participant registration;
- selection of available conference options.

Out of scope:
- payment processing;
- participant accounts;
- editing an already submitted registration.

---

## US-002 — Student registration

As a student,
I want to register using the student registration form,
so that I can attend the conference and activities available to students.

In scope:
- student registration;
- selection of available conference options.

Out of scope:
- external verification of student status;
- participant accounts.

---

## US-003 — Configurable conference options

As a conference organizer,
I want available workshops and optional conference activities to be
configurable,
so that the registration system can be reused when the programme changes.

In scope:
- workshops;
- events;
- meals;
- other optional activities.

Out of scope:
- an administrative configuration UI.

---

## US-004 — Registration confirmation

As a participant,
I want to receive clear confirmation after my registration has been
successfully processed,
so that I know that my registration was received.

---

## US-005 — Reliable registration storage

As a conference organizer,
I want successfully submitted registrations to be stored reliably,
so that registrations are not lost and can be recovered if necessary.

---

## US-006 — Participant email confirmation

As a participant,
I want to receive an email confirming my successful registration,
so that I have a record of it.

---

## US-007 — Organizer notification

As a conference organizer,
I want to be notified when a new participant registers,
so that I can monitor incoming registrations.

---

## US-008 — Registration export

As a conference organizer,
I want to export the current list of registrations in Excel format,
so that I can process and review registrations outside the system.