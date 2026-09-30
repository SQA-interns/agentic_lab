import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  fetchConfig,
  fetchOptions,
  submitRegistration,
  type Category,
  type ClientConfig,
  type OptionsResponse,
  type RegistrationConfirmation,
  type RegistrationRequest,
  type RegistrationType,
} from './api';
import { Captcha } from './Captcha';
import {
  requiredFields,
  validate,
  type Errors,
  type FieldName,
  type FormValues,
} from './validation';

const CATEGORIES: { category: Category; label: string }[] = [
  { category: 'WORKSHOP', label: 'Workshops' },
  { category: 'EVENT', label: 'Events' },
  { category: 'MEAL', label: 'Meals' },
  { category: 'OTHER', label: 'Other activities' },
];

const FIELD_LABELS: Record<keyof FormValues, string> = {
  firstName: 'First name',
  lastName: 'Last name',
  email: 'Email',
  organization: 'Organization / institution',
  studyInstitution: 'Study institution',
  studyProgramme: 'Study programme',
  studentId: 'Student ID',
};

const EMPTY: FormValues = {
  firstName: '',
  lastName: '',
  email: '',
  organization: '',
  studyInstitution: '',
  studyProgramme: '',
  studentId: '',
};

function TextField(props: {
  name: keyof FormValues;
  value: string;
  error?: string;
  onChange: (name: keyof FormValues, value: string) => void;
}) {
  const { name, value, error, onChange } = props;
  const errorId = `${name}-error`;
  return (
    <div className="field">
      <label htmlFor={name}>
        {FIELD_LABELS[name]} <span aria-hidden="true">*</span>
      </label>
      <input
        id={name}
        name={name}
        type={name === 'email' ? 'email' : 'text'}
        autoComplete={name === 'email' ? 'email' : 'off'}
        value={value}
        required
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={(e) => onChange(name, e.target.value)}
      />
      {error && (
        <p className="error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}

function Confirmation({ confirmation }: { confirmation: RegistrationConfirmation }) {
  return (
    <section className="confirmation" aria-live="polite">
      <h2>Registration received</h2>
      <p>
        Thank you, {confirmation.firstName} {confirmation.lastName}. Your registration reference is{' '}
        <strong>{confirmation.reference}</strong>.
      </p>
      <p>A confirmation email is on its way to {confirmation.email}.</p>
      {confirmation.options.length > 0 ? (
        <>
          <h3>Your selection</h3>
          <ul>
            {confirmation.options.map((o) => (
              <li key={o.id}>{o.name}</li>
            ))}
          </ul>
        </>
      ) : (
        <p>You did not select any optional activities.</p>
      )}
    </section>
  );
}

export function App() {
  const [config, setConfig] = useState<ClientConfig | null>(null);
  const [type, setType] = useState<RegistrationType>('EXTERNAL');
  const [catalog, setCatalog] = useState<OptionsResponse | null>(null);
  const [values, setValues] = useState<FormValues>(EMPTY);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [consents, setConsents] = useState<Set<string>>(new Set());
  const [captchaToken, setCaptchaToken] = useState('');
  const [captchaResets, setCaptchaResets] = useState(0);
  const [errors, setErrors] = useState<Errors>({});
  const [generalError, setGeneralError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [confirmation, setConfirmation] = useState<RegistrationConfirmation | null>(null);

  useEffect(() => {
    fetchConfig()
      .then(setConfig)
      .catch(() =>
        setLoadError('The registration form could not be loaded. Please try again later.'),
      );
  }, []);

  useEffect(() => {
    let current = true;
    fetchOptions(type)
      .then((response) => {
        if (!current) return;
        setCatalog(response);
        const offered = new Set(response.options.map((o) => o.id));
        setSelected((previous) => new Set([...previous].filter((id) => offered.has(id))));
      })
      .catch(() => {
        if (current)
          setLoadError('The registration form could not be loaded. Please try again later.');
      });
    return () => {
      current = false;
    };
  }, [type]);

  const onToken = useCallback((token: string) => {
    setCaptchaToken(token);
    setErrors((e) => ({ ...e, captchaToken: undefined }));
  }, []);

  const grouped = useMemo(
    () =>
      CATEGORIES.map(({ category, label }) => ({
        category,
        label,
        limit: catalog?.categoryLimits[category],
        options: catalog?.options.filter((o) => o.category === category) ?? [],
      })).filter((g) => g.options.length > 0),
    [catalog],
  );

  const setField = (name: keyof FormValues, value: string) => {
    setValues((v) => ({ ...v, [name]: value }));
    setErrors((e) => ({ ...e, [name]: undefined }));
  };

  const toggle = (set: Set<string>, id: string) => {
    const next = new Set(set);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!catalog || submitting) return;
    setGeneralError('');
    const found = validate(type, values, catalog.consents, consents, captchaToken);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const request: RegistrationRequest = {
      type,
      optionIds: catalog.options.filter((o) => selected.has(o.id)).map((o) => o.id),
      consentIds: catalog.consents.filter((c) => consents.has(c.id)).map((c) => c.id),
      captchaToken,
      firstName: '',
      lastName: '',
      email: '',
    };
    for (const field of requiredFields(type)) {
      request[field] = values[field].trim();
    }
    setSubmitting(true);
    const result = await submitRegistration(request);
    setSubmitting(false);
    if (result.ok) {
      setConfirmation(result.confirmation);
      return;
    }
    const fieldErrors: Errors = {};
    for (const e of result.error.errors) {
      fieldErrors[e.field as FieldName] = e.message;
    }
    setErrors(fieldErrors);
    if (result.error.errors.length === 0 || result.status >= 500) {
      setGeneralError(result.error.message);
    }
    if (fieldErrors.captchaToken || !config?.captchaTestMode) {
      setCaptchaToken('');
      setCaptchaResets((n) => n + 1);
    }
  };

  const title = config?.conferenceName ? `${config.conferenceName} – registration` : 'Registration';

  if (confirmation) {
    return (
      <main>
        <h1>{title}</h1>
        <Confirmation confirmation={confirmation} />
      </main>
    );
  }

  const consentError = errors.consentIds;
  const optionsError = errors.optionIds;

  return (
    <main>
      <h1>{title}</h1>
      {loadError && <p role="alert">{loadError}</p>}
      <form noValidate onSubmit={onSubmit}>
        <fieldset>
          <legend>Registration type</legend>
          {(
            [
              ['EXTERNAL', 'External participant'],
              ['STUDENT', 'Student'],
            ] as const
          ).map(([value, label]) => (
            <label key={value} className="radio">
              <input
                type="radio"
                name="type"
                value={value}
                checked={type === value}
                onChange={() => {
                  setType(value);
                  setErrors({});
                }}
              />
              {label}
            </label>
          ))}
        </fieldset>

        <fieldset>
          <legend>Your details</legend>
          {requiredFields(type).map((name) => (
            <TextField
              key={name}
              name={name}
              value={values[name]}
              error={errors[name]}
              onChange={setField}
            />
          ))}
        </fieldset>

        {grouped.map((group) => (
          <fieldset key={group.category}>
            <legend>{group.label}</legend>
            {group.limit !== undefined && <p className="hint">Choose at most {group.limit}.</p>}
            {group.options.map((o) => (
              <label key={o.id} className="checkbox">
                <input
                  type="checkbox"
                  checked={selected.has(o.id)}
                  aria-invalid={optionsError ? 'true' : undefined}
                  aria-describedby={optionsError ? 'optionIds-error' : undefined}
                  onChange={() => {
                    setSelected((s) => toggle(s, o.id));
                    setErrors((e) => ({ ...e, optionIds: undefined }));
                  }}
                />
                {o.name}
              </label>
            ))}
          </fieldset>
        ))}
        {optionsError && (
          <p className="error" id="optionIds-error">
            {optionsError}
          </p>
        )}

        {catalog && catalog.consents.length > 0 && (
          <fieldset>
            <legend>Consents</legend>
            {catalog.consents.map((c) => {
              const invalid = Boolean(consentError) && (c.mandatory || !consents.has(c.id));
              return (
                <label key={c.id} className="checkbox">
                  <input
                    type="checkbox"
                    checked={consents.has(c.id)}
                    aria-invalid={invalid ? 'true' : undefined}
                    aria-describedby={invalid ? 'consentIds-error' : undefined}
                    onChange={() => {
                      setConsents((s) => toggle(s, c.id));
                      setErrors((e) => ({ ...e, consentIds: undefined }));
                    }}
                  />
                  {c.text}
                  {c.mandatory && <span className="required"> (required)</span>}
                </label>
              );
            })}
            {consentError && (
              <p className="error" id="consentIds-error">
                {consentError}
              </p>
            )}
          </fieldset>
        )}

        {config && (
          <Captcha
            testMode={config.captchaTestMode}
            siteKey={config.recaptchaSiteKey}
            token={captchaToken}
            resetCount={captchaResets}
            error={errors.captchaToken}
            onToken={onToken}
          />
        )}

        {generalError && (
          <p role="alert" className="error">
            {generalError}
          </p>
        )}

        <button type="submit" disabled={submitting || !catalog}>
          Register
        </button>
      </form>
    </main>
  );
}
