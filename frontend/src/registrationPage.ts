/**
 * Registration page behaviour (specification § 9, US-001, US-002, US-004).
 *
 * One module drives both variants; the page decides which one by passing its variant in.
 * The form structure is built from the backend configuration, so the page has no
 * hard-coded option list and no hard-coded field-rule table (AC-003-08, AC-G-03).
 */
import {
  fetchRegistrationConfig,
  submitRegistration,
  type ConfigOptionGroup,
  type FieldRule,
  type RegistrationConfig,
  type RegistrationRequest,
  type RegistrationSuccess,
  type RegistrationVariant,
} from './api.js';
import { clear, element, requireElement } from './dom.js';
import { labelFor, toErrorMap, trimValue, validateField, validateForm } from './validation.js';

const FIELD_AUTOCOMPLETE: Readonly<Record<string, string>> = {
  firstName: 'given-name',
  lastName: 'family-name',
  email: 'email',
  organization: 'organization',
  studyInstitution: 'organization',
  studyProgramme: 'off',
  studentId: 'off',
};

interface PageElements {
  readonly form: HTMLFormElement;
  readonly fieldsContainer: HTMLElement;
  readonly optionsContainer: HTMLElement;
  readonly consentContainer: HTMLElement;
  readonly summary: HTMLElement;
  readonly submitButton: HTMLButtonElement;
  readonly confirmation: HTMLElement;
  readonly conferenceName: HTMLElement;
}

export function initRegistrationPage(variant: RegistrationVariant): void {
  const elements: PageElements = {
    form: requireElement<HTMLFormElement>('#registration-form'),
    fieldsContainer: requireElement('#fixed-fields'),
    optionsContainer: requireElement('#option-groups'),
    consentContainer: requireElement('#consents'),
    summary: requireElement('#form-summary'),
    submitButton: requireElement<HTMLButtonElement>('#submit-button'),
    confirmation: requireElement('#confirmation'),
    conferenceName: requireElement('#conference-name'),
  };

  void startPage(variant, elements);
}

async function startPage(variant: RegistrationVariant, elements: PageElements): Promise<void> {
  let config: RegistrationConfig;
  try {
    config = await fetchRegistrationConfig(variant);
  } catch {
    // Without the configuration there is no trustworthy form to show, so the page shows
    // the failure instead of a form that could not be submitted anyway.
    elements.form.hidden = true;
    showSummary(
      elements.summary,
      'error',
      'The registration form could not be loaded. Please reload the page or try again later.',
    );
    return;
  }

  elements.conferenceName.textContent = config.conferenceName;
  document.title = `${document.title} — ${config.conferenceName}`;
  renderFields(elements.fieldsContainer, config);
  renderOptionGroups(elements.optionsContainer, config.optionGroups);
  renderConsents(elements.consentContainer, config);
  elements.form.hidden = false;

  elements.form.addEventListener('submit', (event) => {
    event.preventDefault();
    void handleSubmit(variant, config, elements);
  });
}

function renderFields(container: HTMLElement, config: RegistrationConfig): void {
  clear(container);
  for (const field of config.fields) {
    const rule = config.fieldRules[field];
    if (rule === undefined) {
      continue;
    }
    container.append(buildField(field, rule));
  }
}

function buildField(field: string, rule: FieldRule): HTMLElement {
  const wrapper = element('div', { class: 'field' });
  const inputId = `field-${field}`;
  const errorId = `${inputId}-error`;

  const label = element('label', { for: inputId });
  label.append(document.createTextNode(labelFor(field)));
  if (rule.required) {
    label.append(element('span', { class: 'required', 'aria-hidden': 'true' }, ' *'));
    label.append(element('span', { class: 'visually-hidden' }, ' (required)'));
  }

  const input = element('input', {
    id: inputId,
    name: field,
    type: rule.format === 'email' ? 'email' : 'text',
    maxlength: String(rule.maxLength),
    autocomplete: FIELD_AUTOCOMPLETE[field] ?? 'off',
    'aria-describedby': errorId,
  });
  if (rule.required) {
    input.required = true;
  }

  const error = element('p', { class: 'field-error', id: errorId, role: 'alert' });

  // Validating on blur tells the participant about a mistake while the field still has
  // their attention, rather than only after a rejected submission.
  input.addEventListener('blur', () => {
    const message = validateField(field, input.value, rule);
    wrapper.classList.toggle('has-error', message !== null);
    error.textContent = message ?? '';
    if (message === null) {
      input.removeAttribute('aria-invalid');
    } else {
      input.setAttribute('aria-invalid', 'true');
    }
  });

  wrapper.append(label, input, error);
  return wrapper;
}

function renderOptionGroups(container: HTMLElement, groups: readonly ConfigOptionGroup[]): void {
  clear(container);
  for (const group of groups) {
    // A group with no active options for this variant is not shown at all, so the form
    // never offers an empty section (AC-001-02, AC-002-02).
    if (group.options.length === 0) {
      continue;
    }

    const fieldset = element('fieldset', { class: 'option-group' });
    fieldset.append(element('legend', {}, group.displayName));

    for (const option of group.options) {
      const inputId = `option-${option.id}`;
      const row = element('div', { class: 'option' });
      const input = element('input', {
        type: 'checkbox',
        id: inputId,
        name: 'selectedOptionIds',
        value: option.id,
      });
      const label = element('label', { for: inputId }, option.displayName);
      row.append(input, label);
      if (option.description !== undefined) {
        row.append(element('p', { class: 'option-description' }, option.description));
      }
      fieldset.append(row);
    }

    container.append(fieldset);
  }
}

function renderConsents(container: HTMLElement, config: RegistrationConfig): void {
  clear(container);
  for (const consent of config.consents) {
    const inputId = `consent-${consent.id}`;
    const errorId = `${inputId}-error`;
    const wrapper = element('div', { class: 'field consent' });

    const input = element('input', {
      type: 'checkbox',
      id: inputId,
      name: `consents.${consent.id}`,
      'aria-describedby': errorId,
    });
    // Mandatory consent is rendered unchecked and is never pre-selected (AC-G-08).
    input.checked = false;
    if (consent.required) {
      input.required = true;
    }

    const label = element('label', { for: inputId }, consent.text);
    const error = element('p', { class: 'field-error', id: errorId, role: 'alert' });

    wrapper.append(input, label, error);
    container.append(wrapper);
  }
}

function collectValues(
  form: HTMLFormElement,
  config: RegistrationConfig,
): { fields: Record<string, string>; consents: Record<string, boolean>; optionIds: string[] } {
  const fields: Record<string, string> = {};
  for (const field of config.fields) {
    const input = form.elements.namedItem(field);
    fields[field] = input instanceof HTMLInputElement ? input.value : '';
  }

  const consents: Record<string, boolean> = {};
  for (const consent of config.consents) {
    const input = form.querySelector<HTMLInputElement>(`#consent-${consent.id}`);
    consents[consent.id] = input?.checked ?? false;
  }

  const optionIds = Array.from(
    form.querySelectorAll<HTMLInputElement>('input[name="selectedOptionIds"]:checked'),
  ).map((input) => input.value);

  return { fields, consents, optionIds };
}

async function handleSubmit(
  variant: RegistrationVariant,
  config: RegistrationConfig,
  elements: PageElements,
): Promise<void> {
  const { fields, consents, optionIds } = collectValues(elements.form, config);

  const requiredConsents = config.consents.filter((c) => c.required).map((c) => c.id);
  const clientErrors = validateForm({ fields, consents }, config.fieldRules, requiredConsents);
  if (Object.keys(clientErrors).length > 0) {
    applyErrors(elements, clientErrors);
    return;
  }

  const honeypot = elements.form.querySelector<HTMLInputElement>('input[name="website"]');
  const request: RegistrationRequest = {
    variant,
    firstName: trimValue(fields.firstName ?? ''),
    lastName: trimValue(fields.lastName ?? ''),
    email: trimValue(fields.email ?? ''),
    selectedOptionIds: optionIds,
    consents,
    formToken: config.formToken,
    website: honeypot?.value ?? '',
    ...variantFields(variant, fields),
  };

  setBusy(elements, true);
  clearErrors(elements);
  const outcome = await submitRegistration(request);
  setBusy(elements, false);

  switch (outcome.kind) {
    case 'created':
      // The confirmation appears only here, after a 201 from the backend (AC-004-01).
      showConfirmation(elements, outcome.registration);
      return;
    case 'validation':
      applyErrors(elements, toErrorMap(outcome.fields), outcome.message);
      return;
    case 'anti-automation':
      showSummary(elements.summary, 'error', outcome.message);
      return;
    case 'rate-limited':
      showSummary(elements.summary, 'error', outcome.message);
      return;
    case 'technical':
      // Entered data is deliberately left in the form so the participant can retry
      // without typing everything again (AC-004-05).
      showSummary(elements.summary, 'error', outcome.message);
      return;
  }
}

function variantFields(
  variant: RegistrationVariant,
  fields: Record<string, string>,
): Partial<RegistrationRequest> {
  if (variant === 'external') {
    return { organization: trimValue(fields.organization ?? '') };
  }
  return {
    studyInstitution: trimValue(fields.studyInstitution ?? ''),
    studyProgramme: trimValue(fields.studyProgramme ?? ''),
    studentId: trimValue(fields.studentId ?? ''),
  };
}

function setBusy(elements: PageElements, busy: boolean): void {
  // Disabling the control for the duration of the request means one user action can
  // produce at most one submission (AC-004-06).
  elements.submitButton.disabled = busy;
  elements.submitButton.setAttribute('aria-busy', String(busy));
  elements.submitButton.textContent = busy ? 'Sending…' : 'Submit registration';
}

function clearErrors(elements: PageElements): void {
  elements.summary.hidden = true;
  clear(elements.summary);
  for (const wrapper of elements.form.querySelectorAll('.field')) {
    wrapper.classList.remove('has-error');
    const error = wrapper.querySelector('.field-error');
    if (error !== null) {
      error.textContent = '';
    }
    const input = wrapper.querySelector('input');
    input?.removeAttribute('aria-invalid');
  }
}

function applyErrors(
  elements: PageElements,
  errors: Record<string, string>,
  summaryMessage?: string,
): void {
  clearErrors(elements);

  let firstInvalid: HTMLInputElement | null = null;
  const unassigned: string[] = [];

  for (const [key, message] of Object.entries(errors)) {
    const input = inputForErrorKey(elements.form, key);
    if (input === null) {
      unassigned.push(message);
      continue;
    }
    const wrapper = input.closest('.field');
    const error = wrapper?.querySelector('.field-error') ?? null;
    if (wrapper !== null && error !== null) {
      wrapper.classList.add('has-error');
      error.textContent = message;
      input.setAttribute('aria-invalid', 'true');
    } else {
      unassigned.push(message);
    }
    firstInvalid ??= input;
  }

  const lines = [
    summaryMessage ?? 'Some fields need your attention before the registration can be sent.',
    ...unassigned,
  ];
  showSummary(elements.summary, 'error', lines.join(' '));
  firstInvalid?.focus();
}

function inputForErrorKey(form: HTMLFormElement, key: string): HTMLInputElement | null {
  if (key.startsWith('consents.')) {
    return form.querySelector<HTMLInputElement>(`#consent-${key.slice('consents.'.length)}`);
  }
  if (key === 'selectedOptionIds' || key === 'formToken' || key === 'variant' || key === 'body') {
    return null;
  }
  return form.querySelector<HTMLInputElement>(`#field-${key}`);
}

function showSummary(summary: HTMLElement, kind: 'error' | 'info', message: string): void {
  clear(summary);
  summary.className = `summary summary-${kind}`;
  summary.hidden = false;
  summary.append(element('p', {}, message));
}

function showConfirmation(elements: PageElements, registration: RegistrationSuccess): void {
  elements.form.hidden = true;
  elements.summary.hidden = true;

  const confirmation = elements.confirmation;
  clear(confirmation);
  confirmation.append(
    element('h2', {}, 'Your registration has been received'),
    element('p', {}, `Registration reference: ${registration.reference}`),
    element(
      'p',
      {},
      `A confirmation email has been sent to ${registration.confirmationEmailQueuedTo}.`,
    ),
  );

  if (registration.selectedOptions.length > 0) {
    confirmation.append(element('h3', {}, 'Selected activities'));
    const list = element('ul');
    for (const option of registration.selectedOptions) {
      list.append(element('li', {}, option.displayName));
    }
    confirmation.append(list);
  } else {
    confirmation.append(element('p', {}, 'No optional activities were selected.'));
  }

  confirmation.hidden = false;
  confirmation.focus();
}
