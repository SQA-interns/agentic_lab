import { useCallback, useState, type FormEvent } from "react";
import { submitRegistration } from "../api/client";
import type { RegistrationRequest, RegistrationSetup, RegistrationType } from "../api/types";
import { LABELS, MESSAGES, message } from "../messages";
import { FIELDS_BY_TYPE, checkText, strip, type TextField } from "../validation/rules";
import { Captcha } from "./Captcha";
import { OptionGroups } from "./OptionGroups";
import { TextInput } from "./TextInput";

type Errors = Partial<Record<TextField | "consentGiven" | "recaptchaToken", string>>;

const EMPTY: Record<TextField, string> = {
  firstName: "",
  lastName: "",
  email: "",
  organization: "",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
};

interface Props {
  setup: RegistrationSetup;
  onAccepted: (registrationId: string) => void;
}

export function RegistrationForm({ setup, onAccepted }: Props) {
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [values, setValues] = useState(EMPTY);
  const [selected, setSelected] = useState<string[]>([]);
  const [consent, setConsent] = useState(false);
  const [token, setToken] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [optionError, setOptionError] = useState(false);
  const [formError, setFormError] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const offered = setup.options.filter((o) => o.offeredTo.includes(type));
  const fields = FIELDS_BY_TYPE[type];

  const changeType = (next: RegistrationType) => {
    setType(next);
    // Options and fields of the other type are neither shown nor sent (AC-002-05, AC-002-09).
    setSelected((s) =>
      s.filter((id) => setup.options.some((o) => o.id === id && o.offeredTo.includes(next))),
    );
    setErrors({});
  };

  const onChange = (field: TextField, value: string) =>
    setValues((v) => ({ ...v, [field]: value }));

  const onBlur = (field: TextField) =>
    setErrors((e) => ({ ...e, [field]: checkText(field, values[field]) }));

  const toggle = (id: string) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const onToken = useCallback((t: string) => setToken(t), []);

  const validate = (): Errors => {
    const found: Errors = {};
    for (const field of fields) {
      found[field] = checkText(field, values[field]);
    }
    if (!consent) found.consentGiven = "CONSENT_REQUIRED";
    if (!token) found.recaptchaToken = "RECAPTCHA_FAILED";
    return found;
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError(false);
    setOptionError(false);
    const found = validate();
    setErrors(found);
    if (Object.values(found).some(Boolean) || submitting) return;

    const request: RegistrationRequest = {
      type,
      optionIds: selected,
      consentGiven: consent,
      recaptchaToken: token,
      firstName: "",
      lastName: "",
      email: "",
    };
    for (const field of fields) {
      request[field] = strip(values[field]);
    }
    setSubmitting(true);
    const result = await submitRegistration(request);
    setSubmitting(false);
    if (result.kind === "accepted") {
      onAccepted(result.registrationId);
    } else if (result.kind === "duplicate") {
      setErrors({ email: "DUPLICATE_EMAIL" });
    } else if (result.kind === "invalid") {
      showServerErrors(result.errors);
    } else {
      setFormError(true);
    }
  };

  const showServerErrors = (serverErrors: { field: string; code: string }[]) => {
    const mapped: Errors = {};
    let general = false;
    for (const e of serverErrors) {
      if (e.field.startsWith("optionIds")) {
        setOptionError(true);
      } else if (e.field === "consentGiven" || e.field === "recaptchaToken") {
        mapped[e.field] = e.code;
      } else if ((fields as string[]).includes(e.field)) {
        mapped[e.field as TextField] = e.code;
      } else {
        general = true;
      }
    }
    if (tokenRejected(serverErrors)) setToken("");
    setErrors(mapped);
    setFormError(general);
  };

  return (
    <form data-testid="registration-form" noValidate onSubmit={onSubmit}>
      <fieldset className="type">
        <legend>{LABELS.type}</legend>
        {(["EXTERNAL", "STUDENT"] as const).map((t) => (
          <div className="check" key={t}>
            <input
              type="radio"
              name="type"
              id={`type-${t}`}
              data-testid={`type-${t}`}
              checked={type === t}
              onChange={() => changeType(t)}
            />
            <label htmlFor={`type-${t}`}>{LABELS[t]}</label>
          </div>
        ))}
      </fieldset>

      {fields.map((field) => (
        <TextInput
          key={field}
          field={field}
          value={values[field]}
          error={errors[field]}
          type={field === "email" ? "email" : "text"}
          autoComplete={AUTOCOMPLETE[field]}
          onChange={onChange}
          onBlur={onBlur}
        />
      ))}

      <OptionGroups options={offered} selected={selected} error={optionError} onToggle={toggle} />

      <div className="check consent">
        <input
          type="checkbox"
          id="consent"
          data-testid="consent"
          checked={consent}
          aria-describedby={errors.consentGiven ? "error-consentGiven" : undefined}
          onChange={(e) => setConsent(e.target.checked)}
        />
        <label htmlFor="consent" data-testid="consent-text">
          {setup.consent.text}
        </label>
      </div>
      {errors.consentGiven && (
        <p id="error-consentGiven" data-testid="error-consentGiven" className="error">
          {message(errors.consentGiven)}
        </p>
      )}

      <Captcha
        testMode={setup.recaptcha.testMode}
        siteKey={setup.recaptcha.siteKey}
        token={token}
        onToken={onToken}
      />
      {errors.recaptchaToken && (
        <p data-testid="error-recaptchaToken" className="error">
          {message(errors.recaptchaToken)}
        </p>
      )}

      {formError && (
        <p data-testid="form-error" className="error general" role="alert">
          {MESSAGES.GENERAL}
        </p>
      )}

      <button type="submit" data-testid="submit" disabled={submitting}>
        {LABELS.submit}
      </button>
    </form>
  );
}

const AUTOCOMPLETE: Partial<Record<TextField, string>> = {
  firstName: "given-name",
  lastName: "family-name",
  email: "email",
  organization: "organization",
};

/** A reCAPTCHA token is single-use; a rejected one must be renewed. */
function tokenRejected(errors: { field: string; code: string }[]) {
  return errors.some((e) => e.field === "recaptchaToken");
}
