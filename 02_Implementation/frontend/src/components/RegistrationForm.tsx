import { useState, type FormEvent } from "react";
import { submitRegistration, type RegistrationSuccess } from "../api/client";
import {
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  FIELDS_BY_TYPE,
  TYPE_LABELS,
} from "../fields";
import type {
  ConferenceConfig,
  FieldErrors,
  FormValues,
  RegistrationRequest,
  RegistrationType,
} from "../types";
import { MESSAGES, validateForm } from "../validation";
import { Captcha } from "./Captcha";
import { OptionGroup } from "./OptionGroup";

const EMPTY_VALUES: FormValues = {
  firstName: "",
  lastName: "",
  email: "",
  organization: "",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
};

interface RegistrationFormProps {
  config: ConferenceConfig;
  onRegistered: (result: RegistrationSuccess) => void;
}

function toggle(
  set: ReadonlySet<string>,
  id: string,
  checked: boolean,
): Set<string> {
  const next = new Set(set);
  if (checked) {
    next.add(id);
  } else {
    next.delete(id);
  }
  return next;
}

export function RegistrationForm({
  config,
  onRegistered,
}: RegistrationFormProps) {
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [values, setValues] = useState<FormValues>(EMPTY_VALUES);
  const [optionIds, setOptionIds] = useState<Set<string>>(new Set());
  const [consentIds, setConsentIds] = useState<Set<string>>(new Set());
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fields = FIELDS_BY_TYPE[type];

  function resetCaptcha() {
    setCaptchaToken(null);
    setCaptchaReset((n) => n + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setGeneralError(null);
    const clientErrors = validateForm(
      type,
      values,
      config.consents,
      consentIds,
    );
    if (!captchaToken) {
      clientErrors.captcha = MESSAGES.captchaRequired;
    }
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0 || !captchaToken) {
      setGeneralError("Please correct the highlighted fields.");
      return;
    }

    const request: RegistrationRequest = {
      type,
      optionIds: [...optionIds],
      consentIds: [...consentIds],
      recaptchaToken: captchaToken,
    };
    for (const field of fields) {
      request[field.name] = values[field.name];
    }

    setSubmitting(true);
    const result = await submitRegistration(request);
    setSubmitting(false);
    if (result.ok) {
      onRegistered(result.data);
      return;
    }
    const serverErrors: FieldErrors = {};
    for (const fieldError of result.error.fieldErrors) {
      serverErrors[fieldError.field] = fieldError.message;
    }
    setErrors(serverErrors);
    setGeneralError(result.error.message);
    resetCaptcha();
  }

  const optionsByCategory = CATEGORY_ORDER.map((category) => ({
    category,
    options: config.options.filter((option) => option.category === category),
  })).filter((group) => group.options.length > 0);

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-label="Conference registration"
    >
      <fieldset className="type-chooser">
        <legend>Registration type</legend>
        {(Object.keys(TYPE_LABELS) as RegistrationType[]).map((option) => (
          <label key={option} className="radio">
            <input
              type="radio"
              name="type"
              value={option}
              checked={type === option}
              onChange={() => {
                setType(option);
                setErrors({});
                setGeneralError(null);
              }}
            />{" "}
            {TYPE_LABELS[option]}
          </label>
        ))}
      </fieldset>

      <fieldset>
        <legend>Participant details</legend>
        {fields.map((field) => {
          const id = `field-${field.name}`;
          const error = errors[field.name];
          return (
            <div key={field.name} className="field">
              <label htmlFor={id}>
                {field.label} <span aria-hidden="true">*</span>
              </label>
              <input
                id={id}
                name={field.name}
                type={field.inputType}
                required
                maxLength={field.maxLength}
                autoComplete={field.autoComplete}
                value={values[field.name]}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? `${id}-error` : undefined}
                onChange={(event) =>
                  setValues((current) => ({
                    ...current,
                    [field.name]: event.target.value,
                  }))
                }
              />
              {error && (
                <p id={`${id}-error`} className="field-error">
                  {error}
                </p>
              )}
            </div>
          );
        })}
      </fieldset>

      {optionsByCategory.length > 0 && (
        <section className="options" aria-labelledby="options-heading">
          <h2 id="options-heading">Conference options</h2>
          {optionsByCategory.map((group) => (
            <OptionGroup
              key={group.category}
              legend={CATEGORY_LABELS[group.category]}
              options={group.options}
              selected={optionIds}
              onToggle={(id, checked) =>
                setOptionIds((set) => toggle(set, id, checked))
              }
            />
          ))}
          {errors.optionIds && (
            <p className="field-error">{errors.optionIds}</p>
          )}
        </section>
      )}

      {config.consents.length > 0 && (
        <fieldset className="consents">
          <legend>Consent</legend>
          {config.consents.map((consent) => {
            const errorKey = `consents.${consent.id}`;
            const error = errors[errorKey];
            return (
              <div key={consent.id} className="field">
                <label className="checkbox">
                  <input
                    type="checkbox"
                    name="consentIds"
                    value={consent.id}
                    checked={consentIds.has(consent.id)}
                    required={consent.required}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={
                      error ? `consent-${consent.id}-error` : undefined
                    }
                    onChange={(event) =>
                      setConsentIds((set) =>
                        toggle(set, consent.id, event.target.checked),
                      )
                    }
                  />{" "}
                  {consent.label}
                  {consent.required && <span aria-hidden="true"> *</span>}
                </label>
                {error && (
                  <p id={`consent-${consent.id}-error`} className="field-error">
                    {error}
                  </p>
                )}
              </div>
            );
          })}
          {errors.consentIds && (
            <p className="field-error">{errors.consentIds}</p>
          )}
        </fieldset>
      )}

      <Captcha
        siteKey={config.recaptcha.siteKey}
        testMode={config.recaptcha.testMode}
        resetSignal={captchaReset}
        error={errors.captcha}
        onChange={setCaptchaToken}
      />

      {generalError && (
        <p className="form-error" role="alert">
          {generalError}
        </p>
      )}

      <button type="submit" disabled={submitting}>
        {submitting ? "Submitting…" : "Register"}
      </button>
    </form>
  );
}
