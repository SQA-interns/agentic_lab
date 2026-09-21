/**
 * Email composition (specification § 7, US-006, US-007).
 *
 * Every participant-supplied value is HTML-escaped in the HTML part. Values are already
 * free of CR/LF after normalisation, so neither markup nor extra email headers can be
 * injected through a form field (AC-006-05).
 */
import type { Registration } from '../../domain/registration.js';

export interface ComposedMessage {
  readonly subject: string;
  readonly text: string;
  readonly html: string;
}

const HTML_ESCAPES: Readonly<Record<string, string>> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/gu, (character) => HTML_ESCAPES[character] ?? character);
}

const VARIANT_LABEL: Readonly<Record<string, string>> = {
  external: 'External participant',
  student: 'Student',
};

function optionLines(registration: Registration): string[] {
  if (registration.selectedOptions.length === 0) {
    return ['No optional activities selected.'];
  }
  return registration.selectedOptions.map((option) => option.displayName);
}

export function composeParticipantConfirmation(
  registration: Registration,
  conferenceName: string,
): ComposedMessage {
  const fullName = `${registration.participant.firstName} ${registration.participant.lastName}`;
  const options = optionLines(registration);

  const text = [
    `Hello ${fullName},`,
    '',
    `your registration for ${conferenceName} has been received.`,
    '',
    `Registration reference: ${registration.reference}`,
    `Participant type: ${VARIANT_LABEL[registration.variant] ?? registration.variant}`,
    `Registered at: ${registration.createdAt}`,
    '',
    'Selected activities:',
    ...options.map((option) => `  - ${option}`),
    '',
    'Please keep this email as proof of your registration.',
  ].join('\n');

  const html = [
    `<p>Hello ${escapeHtml(fullName)},</p>`,
    `<p>your registration for ${escapeHtml(conferenceName)} has been received.</p>`,
    '<ul>',
    `<li><strong>Registration reference:</strong> ${escapeHtml(registration.reference)}</li>`,
    `<li><strong>Participant type:</strong> ${escapeHtml(VARIANT_LABEL[registration.variant] ?? registration.variant)}</li>`,
    `<li><strong>Registered at:</strong> ${escapeHtml(registration.createdAt)}</li>`,
    '</ul>',
    '<p><strong>Selected activities:</strong></p>',
    '<ul>',
    ...options.map((option) => `<li>${escapeHtml(option)}</li>`),
    '</ul>',
    '<p>Please keep this email as proof of your registration.</p>',
  ].join('\n');

  return {
    subject: `Registration confirmation ${registration.reference} — ${conferenceName}`,
    text,
    html,
  };
}

export function composeOrganizerNotification(
  registration: Registration,
  conferenceName: string,
): ComposedMessage {
  const participant = registration.participant;
  const fields: Array<[string, string]> = [
    ['Reference', registration.reference],
    ['Registered at (UTC)', registration.createdAt],
    ['Participant type', VARIANT_LABEL[registration.variant] ?? registration.variant],
    ['First name', participant.firstName],
    ['Last name', participant.lastName],
    ['Email', participant.email],
  ];
  if (registration.variant === 'external') {
    fields.push(['Organization / institution', participant.organization ?? '']);
  } else {
    fields.push(
      ['Study institution', participant.studyInstitution ?? ''],
      ['Study programme', participant.studyProgramme ?? ''],
      ['Student ID', participant.studentId ?? ''],
    );
  }
  fields.push(['Privacy consent granted at (UTC)', registration.privacyConsentAt]);

  const options = optionLines(registration);

  const text = [
    `New registration for ${conferenceName}.`,
    '',
    ...fields.map(([label, value]) => `${label}: ${value}`),
    '',
    'Selected activities:',
    ...options.map((option) => `  - ${option}`),
    '',
    'The full registration data is attached as a JSON file.',
  ].join('\n');

  const html = [
    `<p>New registration for ${escapeHtml(conferenceName)}.</p>`,
    '<table>',
    ...fields.map(
      ([label, value]) =>
        `<tr><th align="left">${escapeHtml(label)}</th><td>${escapeHtml(value)}</td></tr>`,
    ),
    '</table>',
    '<p><strong>Selected activities:</strong></p>',
    '<ul>',
    ...options.map((option) => `<li>${escapeHtml(option)}</li>`),
    '</ul>',
    '<p>The full registration data is attached as a JSON file.</p>',
  ].join('\n');

  return {
    subject: `New registration ${registration.reference} (${registration.variant}) — ${conferenceName}`,
    text,
    html,
  };
}
