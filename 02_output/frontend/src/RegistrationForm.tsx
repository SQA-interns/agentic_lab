import { useState, type FormEvent } from "react";
import { AntiAutomation } from "./AntiAutomation";
import {
  submitRegistration,
  type FormConfig,
  type RegistrationAccepted,
  type RegistrationRequest,
  type RegistrationType,
} from "./api";
import { texts } from "./texts";
import { FIELDS, categoriesOverLimit, checkText, type TextField } from "./validation";

type Errors = Partial<Record<string, string>>;

const EMPTY: Record<TextField, string> = {
  firstName: "",
  lastName: "",
  email: "",
  organization: "",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
};

const AUTOCOMPLETE: Partial<Record<TextField, string>> = {
  firstName: "given-name",
  lastName: "family-name",
  email: "email",
  organization: "organization",
};

interface Props {
  config: FormConfig;
  onAccepted: (registration: RegistrationAccepted) => void;
}

/** The registration form for both types (US-001, US-002, US-003; ui-registration-form.json). */
export function RegistrationForm({ config, onAccepted }: Props) {
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [values, setValues] = useState(EMPTY);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [consents, setConsents] = useState<Set<string>>(new Set());
  const [token, setToken] = useState<string | null>(null);
  const [resetKey, setResetKey] = useState(0);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fields = FIELDS[type];
  const options = config.options.filter((o) => o.availableTo.includes(type));

  function chooseType(next: RegistrationType) {
    setType(next);
    const available = new Set(
      config.options.filter((o) => o.availableTo.includes(next)).map((o) => o.id),
    );
    setSelected((current) => new Set([...current].filter((id) => available.has(id))));
    setErrors({});
  }

  function fieldError(name: TextField, value: string): string | undefined {
    const code = checkText(value, fields.find((f) => f.name === name)!.maxLength, name === "email");
    return code ? texts.fieldErrors[code] : undefined;
  }

  function validate(): Errors {
    const found: Errors = {};
    for (const field of fields) {
      const message = fieldError(field.name, values[field.name]);
      if (message) {
        found[field.name] = message;
      }
    }
    const visible = new Set(options.filter((o) => selected.has(o.id)).map((o) => o.id));
    for (const category of categoriesOverLimit(config, visible)) {
      found[`category-${category}`] = texts.fieldErrors.CATEGORY_LIMIT;
    }
    if (config.consents.some((c) => !consents.has(c.id))) {
      found.consentIds = texts.consentRequired;
    }
    if (!token) {
      found.antiAutomation = texts.robotRequired;
    }
    return found;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) {
      return;
    }
    const found = validate();
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0 || !token) {
      return;
    }
    const body: RegistrationRequest = {
      type,
      firstName: "",
      lastName: "",
      email: "",
      optionIds: options.filter((o) => selected.has(o.id)).map((o) => o.id),
      consentIds: config.consents.map((c) => c.id),
      antiAutomationToken: token,
    };
    for (const field of fields) {
      body[field.name] = values[field.name].trim();
    }
    setBusy(true);
    const result = await submitRegistration(body);
    setBusy(false);
    if (result.kind === "accepted") {
      onAccepted(result.registration);
      return;
    }
    if (result.kind === "unreachable") {
      setFormError(texts.networkError);
      return;
    }
    const { error } = result;
    const serverErrors: Errors = {};
    for (const fieldErrorBody of error.fieldErrors ?? []) {
      serverErrors[fieldErrorBody.field] = fieldErrorBody.message;
    }
    setErrors(serverErrors);
    if (error.code === "CAPTCHA_FAILED") {
      setToken(null);
      setResetKey((k) => k + 1);
    }
    if (error.code !== "VALIDATION_FAILED" || serverErrors.type) {
      setFormError(error.message);
    }
  }

  function describedBy(key: string) {
    return errors[key] ? `${key}-error` : undefined;
  }

  return (
    <form onSubmit={submit} noValidate>
      <div className="field" role="radiogroup" aria-labelledby="type-label">
        <span id="type-label" className="legend">
          {texts.typeSelector}
        </span>
        {(["EXTERNAL", "STUDENT"] as const).map((t) => (
          <label key={t} className="check">
            <input
              type="radio"
              name="type"
              value={t}
              checked={type === t}
              onChange={() => chooseType(t)}
            />{" "}
            {texts.types[t]}
          </label>
        ))}
      </div>

      {fields.map(({ name, maxLength }) => (
        <div key={name} className="field">
          <label htmlFor={name}>{texts.fields[name]}</label>
          <input
            id={name}
            name={name}
            type={name === "email" ? "email" : "text"}
            autoComplete={AUTOCOMPLETE[name]}
            maxLength={maxLength}
            value={values[name]}
            onChange={(e) => setValues({ ...values, [name]: e.target.value })}
            aria-invalid={errors[name] ? true : undefined}
            aria-describedby={describedBy(name)}
          />
          {errors[name] && (
            <p id={`${name}-error`} className="error">
              {errors[name]}
            </p>
          )}
        </div>
      ))}

      {config.categories.map(({ id }) => {
        const inCategory = options.filter((o) => o.category === id);
        if (inCategory.length === 0) {
          return null;
        }
        const key = `category-${id}`;
        return (
          <fieldset key={id} aria-describedby={describedBy(key)}>
            <legend>{texts.categories[id]}</legend>
            {inCategory.map((option) => (
              <label key={option.id} className="check">
                <input
                  type="checkbox"
                  checked={selected.has(option.id)}
                  onChange={(e) => {
                    const next = new Set(selected);
                    if (e.target.checked) {
                      next.add(option.id);
                    } else {
                      next.delete(option.id);
                    }
                    setSelected(next);
                  }}
                />{" "}
                {option.displayName}
              </label>
            ))}
            {errors[key] && (
              <p id={`${key}-error`} className="error">
                {errors[key]}
              </p>
            )}
          </fieldset>
        );
      })}
      {errors.optionIds && <p className="error">{errors.optionIds}</p>}

      <div className="field">
        {config.consents.map((consent) => (
          <label key={consent.id} className="check">
            <input
              type="checkbox"
              checked={consents.has(consent.id)}
              onChange={(e) => {
                const next = new Set(consents);
                if (e.target.checked) {
                  next.add(consent.id);
                } else {
                  next.delete(consent.id);
                }
                setConsents(next);
              }}
              aria-invalid={errors.consentIds ? true : undefined}
              aria-describedby={describedBy("consentIds")}
            />{" "}
            {consent.text}
          </label>
        ))}
        {errors.consentIds && (
          <p id="consentIds-error" className="error">
            {errors.consentIds}
          </p>
        )}
      </div>

      <AntiAutomation
        settings={config.antiAutomation}
        token={token}
        onToken={setToken}
        resetKey={resetKey}
        error={errors.antiAutomation}
      />

      {formError && (
        <p role="alert" className="alert">
          {formError}
        </p>
      )}
      <button type="submit" disabled={busy}>
        {busy ? texts.submitBusy : texts.submit}
      </button>
    </form>
  );
}
