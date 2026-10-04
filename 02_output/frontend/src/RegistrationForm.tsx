import { useState, type FormEvent } from 'react';
import { submitRegistration, type RegistrationBody } from './api';
import { Captcha } from './Captcha';
import {
  FORMS,
  message,
  OPTION_GROUPS,
  TYPE_CHOICES,
  type FieldName,
  type FormConfig,
  type RegistrationType,
} from './contract';
import { validate, type FieldErrors } from './validation';

export interface Accepted {
  firstName: string;
  email: string;
}

interface Props {
  config: FormConfig;
  onAccepted: (accepted: Accepted) => void;
}

const OPTION_ERRORS = ['UNKNOWN_OPTION', 'INACTIVE_OPTION', 'DUPLICATE_OPTION'];

// The registration form (registration-form.ui.json): type choice, the type's fields, options by
// category, the mandatory consent (never preselected) and the anti-automation check.
export function RegistrationForm({ config, onAccepted }: Props) {
  const [type, setType] = useState<RegistrationType>('EXTERNAL');
  const [values, setValues] = useState<Partial<Record<FieldName, string>>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [consent, setConsent] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [captchaResets, setCaptchaResets] = useState(0);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fields = FORMS[type];

  function toggle(id: string, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  function focusFirstError(found: FieldErrors) {
    const first = fields.find((f) => found[f.field]);
    const id = first ? `f-${first.field}` : found.consentGiven ? 'consent' : null;
    if (id) {
      document.getElementById(id)?.focus();
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    const found = validate(type, values, consent, token);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      focusFirstError(found);
      return;
    }
    const body: RegistrationBody = {
      type,
      optionIds: config.options.filter((o) => selected.has(o.id)).map((o) => o.id),
      consentGiven: consent,
      captchaToken: token ?? '',
    };
    for (const spec of fields) {
      body[spec.field] = values[spec.field] ?? '';
    }
    setSubmitting(true);
    const result = await submitRegistration(body);
    setSubmitting(false);
    if (result.kind === 'accepted') {
      onAccepted({
        firstName: (values.firstName ?? '').trim(),
        email: (values.email ?? '').trim(),
      });
      return;
    }
    setToken(null);
    setCaptchaResets((n) => n + 1);
    if (result.kind === 'invalid') {
      const shown = new Set<string>([
        ...fields.map((f) => f.field),
        'consentGiven',
        'captchaToken',
      ]);
      const mapped: FieldErrors = {};
      let other: string | null = null;
      for (const error of result.errors) {
        if (shown.has(error.field)) {
          mapped[error.field as keyof FieldErrors] ??= error.code;
        } else {
          other = OPTION_ERRORS.includes(error.code) ? error.code : 'GENERIC';
        }
      }
      setErrors(mapped);
      setFormError(other);
      focusFirstError(mapped);
    } else {
      setFormError(result.code);
    }
  }

  const describedBy = (key: keyof FieldErrors) => (errors[key] ? `err-${key}` : undefined);

  return (
    <form noValidate onSubmit={submit}>
      <div className="type-choice" role="radiogroup" aria-labelledby="type-label">
        <span id="type-label" className="group-label">
          Registration type
        </span>
        {TYPE_CHOICES.map((choice) => (
          <span key={choice.type} className="choice">
            <input
              id={`type-${choice.type}`}
              type="radio"
              name="type"
              value={choice.type}
              checked={type === choice.type}
              onChange={() => {
                setType(choice.type);
                setErrors({});
              }}
            />
            <label htmlFor={`type-${choice.type}`}>{choice.label}</label>
          </span>
        ))}
      </div>

      {fields.map((spec) => (
        <div className="field" key={spec.field}>
          <label htmlFor={`f-${spec.field}`}>{spec.label}</label>
          <input
            id={`f-${spec.field}`}
            name={spec.field}
            type={spec.inputType}
            autoComplete={spec.autocomplete}
            maxLength={spec.maxLength}
            value={values[spec.field] ?? ''}
            aria-invalid={errors[spec.field] ? true : undefined}
            aria-describedby={describedBy(spec.field)}
            onChange={(e) => setValues((v) => ({ ...v, [spec.field]: e.target.value }))}
          />
          {errors[spec.field] && (
            <p id={`err-${spec.field}`} className="error">
              {message(errors[spec.field] as string)}
            </p>
          )}
        </div>
      ))}

      {OPTION_GROUPS.map((group) => {
        const options = config.options.filter((o) => o.category === group.category);
        if (options.length === 0) {
          return null;
        }
        return (
          <fieldset key={group.category} className="options">
            <legend>{group.legend}</legend>
            {options.map((option) => (
              <div className="choice" key={option.id}>
                <input
                  id={`o-${option.id}`}
                  type="checkbox"
                  checked={selected.has(option.id)}
                  onChange={(e) => toggle(option.id, e.target.checked)}
                />
                <label htmlFor={`o-${option.id}`}>{option.name}</label>
              </div>
            ))}
          </fieldset>
        );
      })}

      <div className="consent">
        <input
          id="consent"
          type="checkbox"
          checked={consent}
          aria-invalid={errors.consentGiven ? true : undefined}
          aria-describedby={describedBy('consentGiven')}
          onChange={(e) => setConsent(e.target.checked)}
        />
        <label htmlFor="consent">{config.consent.text}</label>
        {errors.consentGiven && (
          <p id="err-consentGiven" className="error">
            {message(errors.consentGiven)}
          </p>
        )}
      </div>

      <Captcha
        testMode={config.captcha.testMode}
        siteKey={config.captcha.siteKey}
        token={token}
        resetCount={captchaResets}
        errorId={describedBy('captchaToken')}
        onToken={setToken}
      />
      {errors.captchaToken && (
        <p id="err-captchaToken" className="error">
          {message(errors.captchaToken)}
        </p>
      )}

      {formError && (
        <p role="alert" className="form-error">
          {message(formError)}
        </p>
      )}
      <button type="submit" disabled={submitting}>
        Register
      </button>
    </form>
  );
}
