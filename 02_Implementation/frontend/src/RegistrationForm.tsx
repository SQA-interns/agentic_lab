import { useRef, useState, type FormEvent } from 'react';
import { submitRegistration, type SubmitResult } from './api';
import { Captcha } from './Captcha';
import { ERROR_MESSAGES, FORM_FIELDS, trimValue, validateFields } from './fields';
import {
  OPTION_GROUPS,
  type Accepted,
  type FormConfig,
  type OptionGroupKey,
  type ParticipantType,
} from './types';

interface Props {
  type: ParticipantType;
  config: FormConfig;
}

type Status =
  | { kind: 'editing' }
  | { kind: 'submitting' }
  | { kind: 'accepted'; data: Accepted; email: string };

const EMPTY_SELECTIONS: Record<OptionGroupKey, string[]> = {
  workshops: [],
  events: [],
  meals: [],
  otherActivities: [],
};

function initialConsents(config: FormConfig): Record<string, boolean> {
  return Object.fromEntries(config.consents.map((c) => [c.id, false]));
}

function formError(result: Exclude<SubmitResult, { kind: 'accepted' }>): string {
  if (result.kind === 'network-error') {
    return 'The server could not be reached. Your registration was not confirmed; please try again.';
  }
  switch (result.code) {
    case 'CAPTCHA_INVALID':
      return 'The anti-robot check failed. Please complete it again.';
    case 'RATE_LIMITED':
      return 'Too many submissions. Please wait a minute and try again.';
    case 'STORAGE_UNAVAILABLE':
      return 'Your registration could not be stored right now and was not accepted. Please try again later.';
    case 'REQUEST_ID_CONFLICT':
      return 'This submission conflicts with an earlier one. Please review and submit again.';
    case 'OPTION_INVALID':
      return 'Some selected options are no longer available. Please reload the page.';
    default:
      return 'Your registration was not accepted. Please check the highlighted fields.';
  }
}

/** One of the two fixed forms (US-001 / US-002). Success is shown only after backend acceptance. */
export function RegistrationForm({ type, config }: Props) {
  const fields = FORM_FIELDS[type];
  const [values, setValues] = useState<Record<string, string>>({});
  const [selections, setSelections] = useState<Record<OptionGroupKey, string[]>>(EMPTY_SELECTIONS);
  const [consents, setConsents] = useState<Record<string, boolean>>(() => initialConsents(config));
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaResetKey, setCaptchaResetKey] = useState(0);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formMessage, setFormMessage] = useState<string | null>(null);
  const [status, setStatus] = useState<Status>({ kind: 'editing' });
  // Same payload retried => same request ID (idempotent); any edit => new ID.
  const requestId = useRef<string | null>(null);

  const edited = () => {
    requestId.current = null;
  };

  const reset = () => {
    setValues({});
    setSelections(EMPTY_SELECTIONS);
    setConsents(initialConsents(config));
    setCaptchaToken(null);
    setFieldErrors({});
    setFormMessage(null);
    requestId.current = null;
    setStatus({ kind: 'editing' });
  };

  const toggleOption = (group: OptionGroupKey, id: string, checked: boolean) => {
    edited();
    setSelections((prev) => ({
      ...prev,
      [group]: checked ? [...prev[group], id] : prev[group].filter((x) => x !== id),
    }));
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const errors = validateFields(type, values);
    for (const consent of config.consents) {
      if (consent.required && !consents[consent.id]) {
        errors[`consents.${consent.id}`] = 'CONSENT_REQUIRED';
      }
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFormMessage('Please correct the highlighted fields.');
      return;
    }
    if (captchaToken === null) {
      setFormMessage('Please complete the anti-robot check.');
      return;
    }
    requestId.current ??= crypto.randomUUID();
    setFormMessage(null);
    setStatus({ kind: 'submitting' });
    const trimmed = Object.fromEntries(
      fields.map((f) => [f.name, trimValue(values[f.name] ?? '')]),
    );
    const result = await submitRegistration(type, {
      clientRequestId: requestId.current,
      fields: trimmed,
      selections,
      consents,
      captchaToken,
    });
    if (result.kind === 'accepted') {
      setStatus({ kind: 'accepted', data: result.data, email: trimmed.email ?? '' });
      return;
    }
    setStatus({ kind: 'editing' });
    setFormMessage(formError(result));
    if (result.kind === 'rejected') {
      setFieldErrors(Object.fromEntries(result.errors.map((e) => [e.field, e.code])));
    }
    // Captcha tokens are single-use: require a fresh one for the next attempt.
    setCaptchaToken(null);
    setCaptchaResetKey((k) => k + 1);
  };

  if (status.kind === 'accepted') {
    return (
      <section className="confirmation" role="status" aria-live="polite">
        <h2>Registration received</h2>
        <p>
          Thank you. Your registration for {config.conferenceName} has been accepted and stored.
        </p>
        <p>
          Registration ID:{' '}
          <strong data-testid="registration-id">{status.data.registrationId}</strong>
        </p>
        <p>
          A confirmation email to <strong>{status.email}</strong> has been queued for sending and
          should arrive shortly.
        </p>
        <button type="button" onClick={reset}>
          Register another participant
        </button>
      </section>
    );
  }

  const submitting = status.kind === 'submitting';
  const errorId = (name: string) => `${type}-${name}-error`;

  return (
    <form onSubmit={onSubmit} noValidate aria-label={`${type} registration form`}>
      <fieldset disabled={submitting}>
        <legend>Participant details</legend>
        {fields.map((field) => {
          const error = fieldErrors[field.name];
          const inputId = `${type}-${field.name}`;
          return (
            <div className="field" key={field.name}>
              <label htmlFor={inputId}>
                {field.label} <span aria-hidden="true">*</span>
              </label>
              <input
                id={inputId}
                name={field.name}
                type={field.type}
                required
                maxLength={field.maxLength + 20}
                autoComplete={field.autoComplete}
                value={values[field.name] ?? ''}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? errorId(field.name) : undefined}
                onChange={(e) => {
                  edited();
                  setValues((prev) => ({ ...prev, [field.name]: e.target.value }));
                }}
              />
              {error && (
                <p className="error" id={errorId(field.name)}>
                  {ERROR_MESSAGES[error] ?? 'Invalid value.'}
                </p>
              )}
            </div>
          );
        })}
      </fieldset>

      <fieldset disabled={submitting}>
        <legend>Conference activities</legend>
        {OPTION_GROUPS.map(({ key, label }) => {
          const options = config.optionGroups[key] ?? [];
          if (options.length === 0) {
            return null;
          }
          return (
            <div className="option-group" key={key} role="group" aria-label={label}>
              <h3>{label}</h3>
              {options.map((option) => (
                <label className="checkbox" key={option.id}>
                  <input
                    type="checkbox"
                    name={`${key}:${option.id}`}
                    checked={selections[key].includes(option.id)}
                    onChange={(e) => toggleOption(key, option.id, e.target.checked)}
                  />
                  {option.name}
                </label>
              ))}
            </div>
          );
        })}
      </fieldset>

      {config.consents.length > 0 && (
        <fieldset disabled={submitting}>
          <legend>Consent</legend>
          {config.consents.map((consent) => {
            const key = `consents.${consent.id}`;
            const error = fieldErrors[key];
            return (
              <div className="field" key={consent.id}>
                <label className="checkbox">
                  <input
                    type="checkbox"
                    name={key}
                    checked={consents[consent.id] ?? false}
                    aria-invalid={error ? true : undefined}
                    onChange={(e) => {
                      edited();
                      setConsents((prev) => ({ ...prev, [consent.id]: e.target.checked }));
                    }}
                  />
                  {consent.text}
                  {consent.required && <span aria-hidden="true"> *</span>}
                </label>
                {error && <p className="error">{ERROR_MESSAGES[error]}</p>}
              </div>
            );
          })}
        </fieldset>
      )}

      <Captcha
        config={config.captcha}
        token={captchaToken}
        resetKey={captchaResetKey}
        onToken={setCaptchaToken}
      />

      {formMessage && (
        <p className="form-error" role="alert">
          {formMessage}
        </p>
      )}

      <button type="submit" disabled={submitting}>
        {submitting ? 'Submitting…' : 'Submit registration'}
      </button>
    </form>
  );
}
