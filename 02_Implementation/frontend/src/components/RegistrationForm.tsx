import { useRef, useState, type SyntheticEvent } from 'react';
import { ApiError, submitRegistration, type RegistrationPayload } from '../api/client';
import { FIELDS, TITLES } from '../formConfig';
import type { FieldName, RegistrationResponse, RegistrationType } from '../types';
import { normalize, validateField, validateForm, type FieldErrors } from '../validation/rules';
import { Confirmation } from './Confirmation';
import { ConsentField } from './ConsentField';
import { OptionGroups } from './OptionGroups';
import { TextField } from './TextField';
import { useFormBootstrap } from './useFormBootstrap';

interface RegistrationFormProps {
  type: RegistrationType;
}

type Values = Partial<Record<FieldName, string>>;

function generalMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.';
  }
  switch (error.code) {
    case 'VALIDATION_FAILED':
      return 'Please correct the highlighted fields.';
    case 'RATE_LIMITED':
      return 'Too many attempts. Please wait a few minutes and try again.';
    case 'SUBMISSION_REJECTED':
      return 'Your submission could not be accepted. Please wait a moment and submit again.';
    case 'NETWORK_ERROR':
      return 'The server could not be reached. Please check your connection and try again.';
    default:
      return 'Registration failed due to a server error. Your data was kept – please try again.';
  }
}

function withFieldError(
  errors: FieldErrors,
  name: keyof FieldErrors,
  message: string | undefined,
): FieldErrors {
  const next: FieldErrors = Object.fromEntries(
    Object.entries(errors).filter(([key]) => key !== name),
  );
  if (message !== undefined) {
    next[name] = message;
  }
  return next;
}

export function RegistrationForm({ type }: RegistrationFormProps) {
  const fields = FIELDS[type];
  const { options, token, loadError, refreshToken } = useFormBootstrap(type);
  const [values, setValues] = useState<Values>({});
  const [consent, setConsent] = useState(false);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [honeypot, setHoneypot] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<RegistrationResponse | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

  if (result !== null) {
    return (
      <Confirmation
        result={result}
        firstName={normalize(values.firstName ?? '')}
        email={normalize(values.email ?? '')}
      />
    );
  }

  const setFieldError = (name: keyof FieldErrors, message: string | undefined) => {
    setErrors((prev) => withFieldError(prev, name, message));
  };

  const focusFirstInvalid = () => {
    requestAnimationFrame(() => {
      formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    });
  };

  const toggleOption = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  };

  const buildPayload = (): RegistrationPayload => {
    const payload: RegistrationPayload = {};
    for (const field of fields) {
      payload[field.name] = normalize(values[field.name] ?? '');
    }
    payload.privacyConsent = consent;
    payload.optionIds = [...selected];
    payload.formToken = token ?? '';
    payload.website = honeypot;
    return payload;
  };

  const handleSubmit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const clientErrors = validateForm(fields, values, consent);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      setGeneralError('Please correct the highlighted fields.');
      focusFirstInvalid();
      return;
    }
    setSubmitting(true);
    setGeneralError(null);
    try {
      setResult(await submitRegistration(type, buildPayload()));
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors);
        if (error.code === 'SUBMISSION_REJECTED') {
          refreshToken();
        }
      }
      setGeneralError(generalMessage(error));
      focusFirstInvalid();
    } finally {
      setSubmitting(false);
    }
  };

  if (loadError) {
    return (
      <p className="banner banner-error" role="alert">
        The registration form could not be loaded. Please reload the page.
      </p>
    );
  }

  return (
    <form
      ref={formRef}
      className="registration-form"
      noValidate
      onSubmit={(e) => {
        void handleSubmit(e);
      }}
    >
      <h2>{TITLES[type]}</h2>
      <p className="hint">Fields marked with * are required.</p>
      {generalError !== null && (
        <p className="banner banner-error" role="alert">
          {generalError}
        </p>
      )}
      {fields.map((field) => (
        <TextField
          key={field.name}
          field={field}
          value={values[field.name] ?? ''}
          error={errors[field.name]}
          disabled={submitting}
          onChange={(value) => {
            setValues((prev) => ({ ...prev, [field.name]: value }));
          }}
          onBlur={() => {
            setFieldError(field.name, validateField(field.kind, values[field.name] ?? ''));
          }}
        />
      ))}

      {options === null ? (
        <p className="hint">Loading conference options…</p>
      ) : (
        <OptionGroups
          options={options}
          selected={selected}
          disabled={submitting}
          error={errors.optionIds}
          onToggle={toggleOption}
        />
      )}

      <div className="honeypot" aria-hidden="true">
        <label htmlFor="field-website">Website</label>
        <input
          id="field-website"
          name="website"
          type="text"
          tabIndex={-1}
          autoComplete="off"
          value={honeypot}
          onChange={(e) => {
            setHoneypot(e.target.value);
          }}
        />
      </div>

      <ConsentField
        checked={consent}
        error={errors.privacyConsent}
        disabled={submitting}
        onChange={(checked) => {
          setConsent(checked);
          if (checked) {
            setFieldError('privacyConsent', undefined);
          }
        }}
      />

      <button type="submit" disabled={submitting || token === null}>
        {submitting ? 'Submitting…' : 'Register'}
      </button>
    </form>
  );
}
