/**
 * Unit tests — email composition and escaping (specification § 7, US-006, US-007).
 *
 * Level justification: the escaping rule is the defence that keeps a participant's name
 * from becoming markup in the organizer's mail client (AC-006-05). Asserting on the
 * composed message directly is precise; asserting through a mail transport would test
 * Nodemailer rather than the rule.
 */
import { describe, expect, it } from 'vitest';

import type { Registration } from '../../src/domain/registration.js';
import {
  composeOrganizerNotification,
  composeParticipantConfirmation,
  escapeHtml,
} from '../../src/infrastructure/mail/templates.js';

const registration: Registration = {
  reference: 'REG-20260921-ABCDEFGHJK',
  variant: 'student',
  participant: {
    firstName: 'Žan',
    lastName: 'Šuštaršič',
    email: 'zan@example.org',
    organization: null,
    studyInstitution: 'Univerza v Mariboru',
    studyProgramme: 'Računalništvo',
    studentId: 'F1234567',
  },
  selectedOptions: [{ optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' }],
  privacyConsent: true,
  privacyConsentAt: '2026-09-21T10:00:00.000Z',
  createdAt: '2026-09-21T10:00:00.000Z',
};

describe('escapeHtml', () => {
  it('escapes every character that could start markup or break an attribute', () => {
    expect(escapeHtml(`<script>alert("x") & 'y'</script>`)).toBe(
      '&lt;script&gt;alert(&quot;x&quot;) &amp; &#39;y&#39;&lt;/script&gt;',
    );
  });

  it('leaves ordinary Unicode text untouched', () => {
    expect(escapeHtml('Šuštaršič')).toBe('Šuštaršič');
  });
});

describe('composeParticipantConfirmation', () => {
  it('contains the reference, the name, the participant type and the option names (AC-006-02)', () => {
    const message = composeParticipantConfirmation(registration, 'Test Conference');
    expect(message.subject).toContain(registration.reference);
    expect(message.text).toContain('Žan Šuštaršič');
    expect(message.text).toContain('REG-20260921-ABCDEFGHJK');
    expect(message.text).toContain('Student');
    expect(message.text).toContain('Workshop: AI');
    expect(message.text).toContain('Test Conference');
  });

  it('states explicitly when nothing was selected, instead of showing an empty list', () => {
    const message = composeParticipantConfirmation({ ...registration, selectedOptions: [] }, 'Test Conference');
    expect(message.text).toContain('No optional activities selected.');
  });

  it('escapes participant values in the HTML part (AC-006-05)', () => {
    const hostile = {
      ...registration,
      participant: { ...registration.participant, firstName: '<img src=x onerror=alert(1)>' },
    };
    const message = composeParticipantConfirmation(hostile, 'Test Conference');
    expect(message.html).not.toContain('<img');
    expect(message.html).toContain('&lt;img');
  });
});

describe('composeOrganizerNotification', () => {
  it('contains every fixed field of the student variant (AC-007-02)', () => {
    const message = composeOrganizerNotification(registration, 'Test Conference');
    expect(message.text).toContain('Univerza v Mariboru');
    expect(message.text).toContain('Računalništvo');
    expect(message.text).toContain('F1234567');
    expect(message.text).toContain('zan@example.org');
    expect(message.text).toContain(registration.createdAt);
  });

  it('shows the organization instead of the study fields for an external participant', () => {
    const external: Registration = {
      ...registration,
      variant: 'external',
      participant: {
        ...registration.participant,
        organization: 'ACME d.o.o.',
        studyInstitution: null,
        studyProgramme: null,
        studentId: null,
      },
    };
    const message = composeOrganizerNotification(external, 'Test Conference');
    expect(message.text).toContain('ACME d.o.o.');
    expect(message.text).not.toContain('Study institution');
  });

  it('escapes participant values in the HTML part', () => {
    const hostile = {
      ...registration,
      participant: { ...registration.participant, lastName: '</td><script>alert(1)</script>' },
    };
    const message = composeOrganizerNotification(hostile, 'Test Conference');
    expect(message.html).not.toContain('<script>');
  });
});
