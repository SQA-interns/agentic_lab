import { useCallback, useState, type FormEvent } from "react";
import {
  submitRegistration,
  type FormConfig,
  type RegistrationRequest,
  type RegistrationResponse,
  type RegistrationType,
} from "../api";
import { FIELDS } from "../fields";
import { validate, type Errors } from "../validation";
import { Captcha } from "./Captcha";
import { OptionGroups } from "./OptionGroups";

interface RegistrationFormProps {
  type: RegistrationType;
  config: FormConfig;
  onRegistered: (registration: RegistrationResponse) => void;
}

const NOT_COMPLETED =
  "Your registration was not completed. Please try again later.";

export function RegistrationForm({
  type,
  config,
  onRegistered,
}: RegistrationFormProps) {
  const fields = FIELDS[type];
  const [values, setValues] = useState<Record<string, string>>({});
  const [selectedOptions, setSelectedOptions] = useState<string[]>([]);
  const [consents, setConsents] = useState<Record<string, boolean>>({});
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onTokenChange = useCallback((token: string | null) => {
    setCaptchaToken(token);
  }, []);

  function toggleOption(id: string) {
    setSelectedOptions((current) =>
      current.includes(id)
        ? current.filter((existing) => existing !== id)
        : [...current, id],
    );
  }

  function resetCaptcha() {
    setCaptchaToken(null);
    setCaptchaReset((n) => n + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const clientErrors = validate(fields, values, config.consents, consents);
    if (!captchaToken) {
      clientErrors.captcha = "Please confirm that you are not a robot.";
    }
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      setFormError("Please correct the highlighted fields.");
      return;
    }

    const request: RegistrationRequest = {
      firstName: "",
      lastName: "",
      email: "",
      optionIds: selectedOptions,
      consents,
      captchaToken: captchaToken ?? "",
    };
    for (const field of fields) {
      request[field.name] = (values[field.name] ?? "").trim();
    }

    setSubmitting(true);
    const result = await submitRegistration(type, request);
    setSubmitting(false);

    if (result.kind === "success") {
      onRegistered(result.registration);
      return;
    }
    resetCaptcha();
    if (result.kind === "invalid") {
      if (result.error.error === "CAPTCHA_FAILED") {
        setErrors({ captcha: "The anti-automation check failed. Try again." });
        setFormError("The anti-automation check failed.");
        return;
      }
      const serverErrors: Errors = {};
      for (const fieldError of result.error.fieldErrors ?? []) {
        serverErrors[fieldError.field] = fieldError.message;
      }
      setErrors(serverErrors);
      setFormError(
        result.error.fieldErrors?.length
          ? "Please correct the highlighted fields."
          : NOT_COMPLETED,
      );
      return;
    }
    setFormError(NOT_COMPLETED);
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label="Registration form">
      {formError && (
        <div className="form-error" role="alert">
          {formError}
        </div>
      )}
      {fields.map((field) => {
        const id = `${type}-${field.name}`;
        const error = errors[field.name];
        return (
          <div className="field" key={field.name}>
            <label htmlFor={id}>{field.label} *</label>
            <input
              id={id}
              name={field.name}
              type={field.type}
              maxLength={field.maxLength}
              autoComplete={field.autoComplete}
              value={values[field.name] ?? ""}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${id}-error` : undefined}
              onChange={(e) =>
                setValues((current) => ({
                  ...current,
                  [field.name]: e.target.value,
                }))
              }
            />
            {error && (
              <p className="field-error" id={`${id}-error`}>
                {error}
              </p>
            )}
          </div>
        );
      })}

      <OptionGroups
        options={config.options}
        selected={selectedOptions}
        onToggle={toggleOption}
        error={errors.optionIds}
      />

      {config.consents.map((consent) => {
        const key = `consents.${consent.id}`;
        return (
          <div className="field" key={consent.id}>
            <label className="checkbox">
              <input
                type="checkbox"
                name={key}
                checked={consents[consent.id] === true}
                onChange={(e) =>
                  setConsents((current) => ({
                    ...current,
                    [consent.id]: e.target.checked,
                  }))
                }
              />
              {consent.text}
              {consent.mandatory && " *"}
            </label>
            {errors[key] && <p className="field-error">{errors[key]}</p>}
          </div>
        );
      })}

      <Captcha
        config={config.captcha}
        token={captchaToken}
        onTokenChange={onTokenChange}
        resetCounter={captchaReset}
        error={errors.captcha}
      />

      <button type="submit" disabled={submitting}>
        {submitting ? "Submitting…" : "Register"}
      </button>
    </form>
  );
}
