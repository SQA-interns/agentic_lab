import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  loadForm,
  submitRegistration,
  type Accepted,
  type FieldError,
  type FormDefinition,
  type RegistrationType,
} from "./api";
import { Captcha } from "./Captcha";
import { categoryHeadings, fieldLabels, message } from "./messages";
import { trimValue, validate } from "./validation";

interface Props {
  type: RegistrationType;
  onAccepted: (registration: Accepted) => void;
}

/** The registration form of one type (US-001, US-002; docs/02_contracts/ui-form.json). */
export function RegistrationForm({ type, onAccepted }: Props) {
  const [form, setForm] = useState<FormDefinition | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [values, setValues] = useState<Record<string, string>>({});
  const [optionIds, setOptionIds] = useState<string[]>([]);
  const [consentIds, setConsentIds] = useState<string[]>([]);
  const [token, setToken] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setForm(null);
    setLoadFailed(false);
    loadForm(type)
      .then((definition) => {
        if (!cancelled) setForm(definition);
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [type]);

  const onToken = useCallback((value: string) => setToken(value), []);

  if (loadFailed) {
    return (
      <p className="error" role="alert" data-testid="form-error">
        {message("GENERAL")}
      </p>
    );
  }
  if (!form) {
    return <p>Loading…</p>;
  }

  const fieldError = (name: string) =>
    errors.find((e) => e.field === name && !e.consentId);
  const consentError = (id: string) =>
    errors.find((e) => e.field === "consentIds" && e.consentId === id);
  const optionsError = errors.find((e) => e.field === "optionIds");
  const captchaError = errors.find((e) => e.field === "recaptchaToken");

  const toggle = (list: string[], id: string, on: boolean) =>
    on ? [...list, id] : list.filter((x) => x !== id);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting || !form) return;
    setFormError("");
    const draft = { values, optionIds, consentIds, recaptchaToken: token };
    const clientErrors = validate(form, draft);
    setErrors(clientErrors);
    if (clientErrors.length > 0) return;
    setSubmitting(true);
    const result = await submitRegistration({
      type,
      values: Object.fromEntries(
        form.fields.map((f) => [f.name, values[f.name] ?? ""]),
      ),
      optionIds,
      consentIds,
      recaptchaToken: token,
    });
    setSubmitting(false);
    if (result.kind === "accepted") {
      onAccepted(result.registration);
      return;
    }
    setToken("");
    setCaptchaReset((n) => n + 1);
    if (result.kind === "rejected") {
      setErrors(result.errors);
      const general = result.errors.find(
        (e) => e.field === "type" || e.code === "UNKNOWN_CONSENT",
      );
      if (general) setFormError(message(general.code));
    } else {
      setFormError(
        message(result.kind === "rateLimited" ? "RATE_LIMITED" : "GENERAL"),
      );
    }
  }

  return (
    <form noValidate onSubmit={(e) => void onSubmit(e)}>
      {form.fields.map((field) => {
        const error = fieldError(field.name);
        const id = `field-${field.name}`;
        return (
          <div className="field" key={field.name}>
            <label htmlFor={id}>{fieldLabels[field.name]}</label>
            <input
              id={id}
              name={field.name}
              data-testid={id}
              type={field.name === "email" ? "email" : "text"}
              autoComplete={field.name === "email" ? "email" : "off"}
              maxLength={field.maxLength}
              value={values[field.name] ?? ""}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `error-${field.name}` : undefined}
              onChange={(e) =>
                setValues({ ...values, [field.name]: e.target.value })
              }
              onBlur={(e) =>
                setValues({
                  ...values,
                  [field.name]: trimValue(e.target.value),
                })
              }
            />
            {error && (
              <p
                className="error"
                id={`error-${field.name}`}
                data-testid={`error-${field.name}`}
              >
                {message(error.code)}
              </p>
            )}
          </div>
        );
      })}

      {form.categories.map((category) => {
        const selected = category.options.filter((o) =>
          optionIds.includes(o.id),
        ).length;
        const full = selected >= category.maxSelections;
        return (
          <fieldset
            className="category"
            key={category.category}
            data-testid={`category-${category.category}`}
          >
            <legend>{categoryHeadings[category.category]}</legend>
            {category.options.length === 0 && (
              <p className="hint">No options available.</p>
            )}
            {category.options.length > 0 && category.maxSelections > 0 && (
              <p className="hint">Choose up to {category.maxSelections}.</p>
            )}
            {category.options.map((option) => {
              const checked = optionIds.includes(option.id);
              return (
                <label className="check" key={option.id}>
                  <input
                    type="checkbox"
                    data-testid={`option-${option.id}`}
                    checked={checked}
                    disabled={!checked && full}
                    onChange={(e) =>
                      setOptionIds(
                        toggle(optionIds, option.id, e.target.checked),
                      )
                    }
                  />
                  {option.name}
                </label>
              );
            })}
          </fieldset>
        );
      })}
      {optionsError && (
        <p className="error" id="error-optionIds" data-testid="error-optionIds">
          {message(optionsError.code)}
        </p>
      )}

      <fieldset className="consents">
        <legend>Consents</legend>
        {form.consents.map((consent) => {
          const error = consentError(consent.id);
          return (
            <div key={consent.id}>
              <label className="check">
                <input
                  type="checkbox"
                  data-testid={`consent-${consent.id}`}
                  checked={consentIds.includes(consent.id)}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={
                    error ? `error-consent-${consent.id}` : undefined
                  }
                  onChange={(e) =>
                    setConsentIds(
                      toggle(consentIds, consent.id, e.target.checked),
                    )
                  }
                />
                {consent.text}
              </label>
              {consent.mandatory && <span className="hint"> (required)</span>}
              {error && (
                <p
                  className="error"
                  id={`error-consent-${consent.id}`}
                  data-testid={`error-consent-${consent.id}`}
                >
                  {message(error.code)}
                </p>
              )}
            </div>
          );
        })}
      </fieldset>

      <Captcha
        mode={form.recaptcha.mode}
        siteKey={form.recaptcha.siteKey}
        token={token}
        resetKey={captchaReset}
        error={captchaError ? message(captchaError.code) : undefined}
        onToken={onToken}
      />

      {formError && (
        <p className="error" role="alert" data-testid="form-error">
          {formError}
        </p>
      )}
      <button type="submit" data-testid="submit" disabled={submitting}>
        Register
      </button>
    </form>
  );
}
