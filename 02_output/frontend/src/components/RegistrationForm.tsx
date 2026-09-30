import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import {
  submitRegistration,
  type Accepted,
  type Catalog,
  type FieldError,
  type FormType,
  type GroupKey,
} from '../api';
import { TEXT_FIELDS, trimValue, validateClient } from '../validation';
import { Captcha } from './Captcha';

interface Props {
  form: FormType;
  catalog: Catalog;
  onAccepted: (accepted: Accepted) => void;
}

const TITLES: Record<FormType, string> = {
  external: 'External participant registration',
  student: 'Student registration',
};

type Selections = Record<GroupKey, string[]>;

const NOT_SAVED = 'Your registration was not saved. Please try again.';

/** One registration form; the client request ID is kept for retries of the same submission. */
export function RegistrationForm({ form, catalog, onAccepted }: Props) {
  const fields = TEXT_FIELDS[form];
  const [clientRequestId] = useState(() => crypto.randomUUID());
  const [values, setValues] = useState<Record<string, string>>({});
  const [selections, setSelections] = useState<Selections>({
    workshops: [],
    events: [],
    meals: [],
    other: [],
  });
  const [consentGiven, setConsentGiven] = useState(false);
  const [captchaToken, setCaptchaToken] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [generalErrors, setGeneralErrors] = useState<string[]>([]);
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const summaryRef = useRef<HTMLDivElement>(null);
  const hasSummary = Object.keys(errors).length > 0 || generalErrors.length > 0;

  useEffect(() => {
    if (attempt > 0 && hasSummary) summaryRef.current?.focus();
  }, [attempt, hasSummary]);

  const onToken = useCallback((token: string) => {
    setCaptchaToken(token);
  }, []);

  const toggle = (group: GroupKey, id: string, checked: boolean) => {
    setSelections((s) => ({
      ...s,
      [group]: checked ? [...s[group], id] : s[group].filter((x) => x !== id),
    }));
  };

  const applyServerErrors = (serverErrors: FieldError[]) => {
    const byField: Record<string, string> = {};
    const general: string[] = [];
    const labels = Object.fromEntries(fields.map((f) => [f.name, f.label]));
    for (const e of serverErrors) {
      const label = labels[e.field];
      if (label) {
        byField[e.field] ??= e.message.includes(label) ? e.message : `${label}: ${e.message}`;
      } else if (e.field === 'consentGiven' || e.field === 'captchaToken') {
        byField[e.field] ??= e.message;
      } else if (e.field.startsWith('selections')) {
        general.push(`Activities: ${e.message}`);
      } else {
        general.push(e.message);
      }
    }
    setErrors(byField);
    setGeneralErrors(general);
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitError('');
    const clientErrors = validateClient({
      form,
      values,
      consentRequired: catalog.consent?.required ?? false,
      consentGiven,
      captchaToken,
    });
    setAttempt((a) => a + 1);
    setGeneralErrors([]);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    const payload: Record<string, unknown> = {
      clientRequestId,
      captchaToken,
      selections,
      consentGiven,
    };
    for (const f of fields) payload[f.name] = trimValue(values[f.name] ?? '');
    setSubmitting(true);
    const result = await submitRegistration(form, payload);
    setSubmitting(false);
    switch (result.kind) {
      case 'accepted':
        onAccepted(result.accepted);
        return;
      case 'invalid':
        applyServerErrors(result.errors);
        setAttempt((a) => a + 1);
        return;
      case 'conflict':
        setSubmitError(
          'This submission conflicts with an earlier one. Reload the page to start a new registration.',
        );
        return;
      case 'rate-limited':
        setSubmitError('Too many registration attempts. Please wait a minute and try again.');
        return;
      default:
        setSubmitError(NOT_SAVED);
    }
  };

  const describedBy = (name: string) => (errors[name] ? `${name}-error` : undefined);

  return (
    <main>
      <h1>{TITLES[form]}</h1>
      <p className="hint">All fields are required unless marked otherwise.</p>
      {hasSummary && (
        <div
          className="summary"
          role="alert"
          tabIndex={-1}
          ref={summaryRef}
          aria-labelledby="summary-title"
        >
          <h2 id="summary-title">Please correct the following</h2>
          <ul>
            {Object.entries(errors).map(([name, message]) => (
              <li key={name}>{message}</li>
            ))}
            {generalErrors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      )}
      <form noValidate onSubmit={(e) => void onSubmit(e)} aria-busy={submitting}>
        {fields.map((f) => (
          <div className="field" key={f.name}>
            <label htmlFor={f.name}>{f.label}</label>
            <input
              id={f.name}
              name={f.name}
              type={f.type ?? 'text'}
              autoComplete={f.type === 'email' ? 'email' : 'off'}
              value={values[f.name] ?? ''}
              aria-invalid={errors[f.name] ? true : undefined}
              aria-describedby={describedBy(f.name)}
              aria-required="true"
              onChange={(e) => {
                setValues((v) => ({ ...v, [f.name]: e.target.value }));
              }}
            />
            {errors[f.name] && (
              <p id={`${f.name}-error`} className="error">
                {errors[f.name]}
              </p>
            )}
          </div>
        ))}

        <h2>Activities (optional)</h2>
        {catalog.groups.map((group) => (
          <fieldset key={group.id}>
            <legend>{group.label}</legend>
            {group.options.length === 0 && <p className="hint">No options available.</p>}
            {group.options.map((option) => {
              const id = `opt-${option.id}`;
              return (
                <div className="checkbox" key={option.id}>
                  <input
                    id={id}
                    type="checkbox"
                    checked={selections[group.id].includes(option.id)}
                    onChange={(e) => {
                      toggle(group.id, option.id, e.target.checked);
                    }}
                  />
                  <label htmlFor={id}>{option.name}</label>
                </div>
              );
            })}
          </fieldset>
        ))}

        {catalog.consent && (
          <div className="field checkbox">
            <input
              id="consentGiven"
              type="checkbox"
              checked={consentGiven}
              aria-invalid={errors.consentGiven ? true : undefined}
              aria-describedby={describedBy('consentGiven')}
              onChange={(e) => {
                setConsentGiven(e.target.checked);
              }}
            />
            <label htmlFor="consentGiven">{catalog.consent.text}</label>
            {errors.consentGiven && (
              <p id="consentGiven-error" className="error">
                {errors.consentGiven}
              </p>
            )}
          </div>
        )}

        <Captcha
          mode={catalog.captcha.mode}
          siteKey={catalog.captcha.siteKey}
          token={captchaToken}
          error={errors.captchaToken}
          onToken={onToken}
        />

        {submitError && (
          <p className="error save-error" role="alert">
            {submitError}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit registration'}
        </button>
      </form>
    </main>
  );
}
