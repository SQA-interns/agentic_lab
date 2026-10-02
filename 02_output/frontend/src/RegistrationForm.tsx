import { type FormEvent, useId, useState } from "react";
import {
  type ConferenceOption,
  type FieldName,
  type FormConfig,
  type RegistrationRequest,
  type RegistrationType,
  submitRegistration,
} from "./api";
import { Captcha } from "./Captcha";
import {
  FAILURES,
  FALLBACK_MESSAGE,
  FORM_FIELDS,
  MESSAGES,
  OPTION_GROUPS,
  SUBMIT_LABEL,
  TYPE_LABELS,
  TYPE_LEGEND,
} from "./texts";
import { validate } from "./validation";

const TYPES: RegistrationType[] = ["EXTERNAL", "STUDENT"];
/** Fields of an API field error that have a place on the form. */
const OTHER_ERROR_FIELDS = ["optionIds", "consent", "captchaToken"];

interface RegistrationFormProps {
  config: FormConfig;
  options: ConferenceOption[];
  onAccepted: () => void;
}

/** The registration form (docs/02_contracts/registration-form.ui.json). */
export function RegistrationForm({ config, options, onAccepted }: RegistrationFormProps) {
  const idPrefix = useId();
  const [type, setType] = useState<RegistrationType | null>(null);
  const [texts, setTexts] = useState<Partial<Record<FieldName, string>>>({});
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [consent, setConsent] = useState(false);
  const [captchaToken, setCaptchaToken] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const fields = type ? FORM_FIELDS[type] : [];
  const errorId = (field: string) => (errors[field] ? `${idPrefix}-${field}-error` : undefined);

  function chooseType(next: RegistrationType) {
    setType(next);
    setErrors({});
    setFailure(null);
  }

  function toggleOption(id: string, checked: boolean) {
    const next = new Set(selected);
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    setSelected(next);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!type || sending) {
      return;
    }
    setFailure(null);
    const codes = validate(fields, { texts, consent, captchaToken });
    const clientErrors: Record<string, string> = {};
    for (const [field, code] of Object.entries(codes)) {
      clientErrors[field] = MESSAGES[code] ?? FALLBACK_MESSAGE;
    }
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    const request: RegistrationRequest = {
      type,
      optionIds: options.filter((option) => selected.has(option.id)).map((option) => option.id),
      consent,
      captchaToken,
    };
    for (const field of fields) {
      request[field.name] = (texts[field.name] ?? "").trim();
    }

    setSending(true);
    const result = await submitRegistration(request);
    setSending(false);
    if (result.kind === "accepted") {
      onAccepted();
      return;
    }
    // A token is valid for one verification only, so the next attempt needs a new check.
    setCaptchaToken("");
    setCaptchaReset((count) => count + 1);
    if (result.kind === "tooManyRequests") {
      setFailure(FAILURES.tooManyRequests);
      return;
    }
    if (result.kind === "notReceived") {
      setFailure(FAILURES.notReceived);
      return;
    }
    const known = new Set<string>([...fields.map((field) => field.name), ...OTHER_ERROR_FIELDS]);
    const serverErrors: Record<string, string> = {};
    for (const error of result.errors) {
      if (known.has(error.field)) {
        serverErrors[error.field] = error.message || MESSAGES[error.code] || FALLBACK_MESSAGE;
      } else {
        setFailure(FAILURES.notReceived);
      }
    }
    setErrors(serverErrors);
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)}>
      <fieldset role="radiogroup">
        <legend>{TYPE_LEGEND}</legend>
        {TYPES.map((value) => (
          <label key={value} className="choice">
            <input
              type="radio"
              name={`${idPrefix}-type`}
              checked={type === value}
              onChange={() => chooseType(value)}
            />{" "}
            {TYPE_LABELS[value]}
          </label>
        ))}
      </fieldset>

      {type && (
        <>
          {fields.map((field) => {
            const inputId = `${idPrefix}-${field.name}`;
            return (
              <div key={field.name} className="field">
                <label htmlFor={inputId}>{field.label}</label>
                <input
                  id={inputId}
                  type={field.control}
                  maxLength={field.maxLength}
                  autoComplete={field.autoComplete}
                  value={texts[field.name] ?? ""}
                  aria-required="true"
                  aria-invalid={errors[field.name] ? true : undefined}
                  aria-describedby={errorId(field.name)}
                  onChange={(event) => setTexts({ ...texts, [field.name]: event.target.value })}
                />
                {errors[field.name] && (
                  <p id={errorId(field.name)} className="error">
                    {errors[field.name]}
                  </p>
                )}
              </div>
            );
          })}

          {OPTION_GROUPS.map((group) => {
            const groupOptions = options.filter((option) => option.category === group.category);
            if (groupOptions.length === 0) {
              return null;
            }
            return (
              <fieldset key={group.category}>
                <legend>{group.legend}</legend>
                {groupOptions.map((option) => (
                  <label key={option.id} className="choice">
                    <input
                      type="checkbox"
                      checked={selected.has(option.id)}
                      onChange={(event) => toggleOption(option.id, event.target.checked)}
                    />{" "}
                    {option.name}
                  </label>
                ))}
              </fieldset>
            );
          })}
          {errors.optionIds && <p className="error">{errors.optionIds}</p>}

          <div className="field">
            <label className="choice">
              <input
                type="checkbox"
                checked={consent}
                aria-required="true"
                aria-invalid={errors.consent ? true : undefined}
                aria-describedby={errorId("consent")}
                onChange={(event) => setConsent(event.target.checked)}
              />{" "}
              {config.consent.text}
            </label>
            {errors.consent && (
              <p id={errorId("consent")} className="error">
                {errors.consent}
              </p>
            )}
          </div>

          <div className="field">
            <Captcha
              captcha={config.captcha}
              token={captchaToken}
              onToken={setCaptchaToken}
              resetSignal={captchaReset}
              errorId={errorId("captchaToken")}
            />
            {errors.captchaToken && (
              <p id={errorId("captchaToken")} className="error">
                {errors.captchaToken}
              </p>
            )}
          </div>

          {failure && (
            <p role="alert" className="failure">
              {failure}
            </p>
          )}

          <button type="submit" disabled={sending}>
            {SUBMIT_LABEL}
          </button>
        </>
      )}
    </form>
  );
}
