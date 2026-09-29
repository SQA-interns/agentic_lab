import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  fetchClientConfig,
  fetchOptions,
  submitRegistration,
  type ClientConfig,
  type ConferenceOption,
  type RegistrationRequest,
  type RegistrationResponse,
  type RegistrationType,
} from "../api/client";
import {
  FIELD_LIMITS,
  fieldsFor,
  messageForCode,
  validate,
  type FieldErrors,
  type FieldName,
  type FormValues,
} from "../validation/validate";
import { Captcha } from "./Captcha";
import { Confirmation } from "./Confirmation";
import { OptionGroups } from "./OptionGroups";
import { TextField } from "./TextField";

const LABELS: Record<keyof typeof FIELD_LIMITS, string> = {
  firstName: "First name",
  lastName: "Last name",
  email: "Email",
  organization: "Organization / institution",
  studyInstitution: "Study institution",
  studyProgramme: "Study programme",
  studentId: "Student ID",
};

export const CONSENT_TEXT =
  "I agree to the processing of my personal data for the purpose of conference registration.";
export const GENERAL_FAILURE =
  "Registration could not be completed. Please try again later.";

const EMPTY: FormValues = {
  type: "EXTERNAL",
  firstName: "",
  lastName: "",
  email: "",
  organization: "",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
  personalDataConsent: false,
  recaptchaToken: "",
};

function toRequest(
  values: FormValues,
  optionIds: string[],
): RegistrationRequest {
  const request: RegistrationRequest = {
    type: values.type,
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    email: values.email.trim(),
    optionIds,
    personalDataConsent: values.personalDataConsent,
    recaptchaToken: values.recaptchaToken,
  };
  for (const field of fieldsFor(values.type)) {
    request[field] = values[field].trim();
  }
  return request;
}

function isFieldName(field: string): field is FieldName {
  return (
    field in LABELS ||
    field === "personalDataConsent" ||
    field === "recaptchaToken"
  );
}

export function RegistrationPage() {
  const [options, setOptions] = useState<ConferenceOption[]>([]);
  const [config, setConfig] = useState<ClientConfig | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [values, setValues] = useState<FormValues>(EMPTY);
  const [selected, setSelected] = useState<string[]>([]);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [captchaReset, setCaptchaReset] = useState(0);
  const [registration, setRegistration] = useState<RegistrationResponse | null>(
    null,
  );

  useEffect(() => {
    Promise.all([fetchOptions(), fetchClientConfig()])
      .then(([loadedOptions, loadedConfig]) => {
        setOptions(loadedOptions);
        setConfig(loadedConfig);
      })
      .catch(() => setLoadFailed(true));
  }, []);

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) =>
    setValues((current) => ({ ...current, [key]: value }));

  const onToken = useCallback(
    (token: string) => set("recaptchaToken", token),
    [],
  );

  const chooseType = (type: RegistrationType) => {
    setValues((current) => ({ ...current, type }));
    setErrors({});
  };

  const toggleOption = (id: string, checked: boolean) =>
    setSelected((current) =>
      checked
        ? [...current, id]
        : current.filter((selectedId) => selectedId !== id),
    );

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setGeneralError(null);
    const clientErrors = validate(values);
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setSubmitting(true);
    const activeIds = new Set(options.map((o) => o.id));
    const result = await submitRegistration(
      toRequest(
        values,
        selected.filter((id) => activeIds.has(id)),
      ),
    );
    setSubmitting(false);

    if (result.kind === "created") {
      setRegistration(result.registration);
      return;
    }
    if (result.kind === "rejected") {
      const serverErrors: FieldErrors = {};
      const unplaced: string[] = [];
      for (const e of result.errors) {
        if (isFieldName(e.field)) {
          serverErrors[e.field] = messageForCode(e.code, e.message);
        } else {
          unplaced.push(e.message);
        }
      }
      setErrors(serverErrors);
      if (serverErrors.recaptchaToken) {
        set("recaptchaToken", "");
        setCaptchaReset((n) => n + 1);
      }
      if (unplaced.length > 0) {
        setGeneralError(unplaced.join(" "));
        fetchOptions()
          .then(setOptions)
          .catch(() => undefined);
      }
      return;
    }
    setGeneralError(GENERAL_FAILURE);
  };

  if (registration) {
    return <Confirmation registration={registration} />;
  }

  return (
    <form className="registration-form" onSubmit={onSubmit} noValidate>
      {loadFailed && (
        <p role="alert" className="general-error">
          The registration form could not be loaded. Please try again later.
        </p>
      )}

      <fieldset className="type-choice">
        <legend>Registration type</legend>
        <label className="radio">
          <input
            type="radio"
            name="type"
            value="EXTERNAL"
            checked={values.type === "EXTERNAL"}
            onChange={() => chooseType("EXTERNAL")}
          />
          External participant
        </label>
        <label className="radio">
          <input
            type="radio"
            name="type"
            value="STUDENT"
            checked={values.type === "STUDENT"}
            onChange={() => chooseType("STUDENT")}
          />
          Student
        </label>
      </fieldset>

      {fieldsFor(values.type).map((field) => (
        <TextField
          key={field}
          name={field}
          label={LABELS[field]}
          value={values[field]}
          onChange={(value) => set(field, value)}
          error={errors[field]}
          type={field === "email" ? "email" : "text"}
          autoComplete={
            field === "firstName"
              ? "given-name"
              : field === "lastName"
                ? "family-name"
                : field === "email"
                  ? "email"
                  : undefined
          }
          maxLength={FIELD_LIMITS[field]}
        />
      ))}

      <OptionGroups
        options={options}
        selected={selected}
        onToggle={toggleOption}
      />

      <div className="field">
        <label className="checkbox">
          <input
            type="checkbox"
            checked={values.personalDataConsent}
            onChange={(e) => set("personalDataConsent", e.target.checked)}
            aria-invalid={errors.personalDataConsent ? true : undefined}
            aria-describedby={
              errors.personalDataConsent
                ? "field-personalDataConsent-error"
                : undefined
            }
          />
          {CONSENT_TEXT}
        </label>
        {errors.personalDataConsent && (
          <p id="field-personalDataConsent-error" className="field-error">
            {errors.personalDataConsent}
          </p>
        )}
      </div>

      {config && (
        <Captcha
          testMode={config.recaptchaTestMode}
          siteKey={config.recaptchaSiteKey}
          token={values.recaptchaToken}
          onToken={onToken}
          resetCounter={captchaReset}
          error={errors.recaptchaToken}
        />
      )}

      {generalError && (
        <p role="alert" className="general-error">
          {generalError}
        </p>
      )}

      <button type="submit" disabled={submitting}>
        Register
      </button>
    </form>
  );
}
