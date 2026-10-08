// The registration form (docs/02_contracts/ui-form.json): one form, two registration types.
import { useCallback, useState, type FormEvent } from "react";
import {
  submitRegistration,
  type Category,
  type FormConfig,
  type RegistrationRequest,
  type RegistrationType,
} from "./api";
import { Recaptcha } from "./Recaptcha";
import { clean, FIELDS, MESSAGES, validate, type Errors, type Values } from "./validation";

const LABELS: Record<string, string> = {
  firstName: "First name",
  lastName: "Last name",
  email: "Email",
  organization: "Organization / institution",
  studyInstitution: "Study institution",
  studyProgramme: "Study programme",
  studentId: "Student ID",
};

const HEADINGS: Record<Category, string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};

const CATEGORY_ORDER: Category[] = ["WORKSHOP", "EVENT", "MEAL", "OTHER"];

interface Props {
  config: FormConfig;
  onAccepted: () => void;
}

export function RegistrationForm({ config, onAccepted }: Props) {
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [values, setValues] = useState<Values>({});
  const [optionIds, setOptionIds] = useState<string[]>([]);
  const [consentIds, setConsentIds] = useState<string[]>([]);
  const [token, setToken] = useState("");
  const [resetKey, setResetKey] = useState(0);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const onToken = useCallback((value: string) => setToken(value), []);

  const offered = config.options.filter((o) => o.registrationTypes.includes(type));

  const chooseType = (next: RegistrationType) => {
    setType(next);
    setOptionIds((ids) =>
      ids.filter((id) =>
        config.options.some((o) => o.id === id && o.registrationTypes.includes(next)),
      ),
    );
    setErrors({});
  };

  const toggle = (list: string[], id: string, on: boolean) =>
    on ? [...list, id] : list.filter((x) => x !== id);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    setFormError("");
    const found = validate(type, values, optionIds, consentIds, token, config);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    const request: RegistrationRequest = {
      type,
      firstName: "",
      lastName: "",
      email: "",
      optionIds: config.options.filter((o) => optionIds.includes(o.id)).map((o) => o.id),
      consentIds: config.consents.filter((c) => consentIds.includes(c.id)).map((c) => c.id),
      recaptchaToken: token,
    };
    for (const { name } of FIELDS[type]) {
      (request as unknown as Record<string, string>)[name] = clean(values[name]);
    }
    setSubmitting(true);
    const result = await submitRegistration(request);
    setSubmitting(false);
    if (result.kind === "accepted") {
      onAccepted();
      return;
    }
    if (!config.recaptcha.testMode) {
      setToken("");
      setResetKey((k) => k + 1);
    }
    if (result.kind === "invalid") {
      const mapped: Errors = {};
      const known = new Set([
        ...FIELDS[type].map((f) => f.name),
        "optionIds",
        "consentIds",
        "recaptchaToken",
      ]);
      for (const { field, code } of result.errors) {
        if (known.has(field)) {
          mapped[field] = mapped[field] ?? MESSAGES[code] ?? MESSAGES.malformed;
        } else {
          setFormError(MESSAGES[code] ?? MESSAGES.malformed);
        }
      }
      setErrors(mapped);
      return;
    }
    setFormError(result.title);
  };

  const error = (field: string) =>
    errors[field] ? (
      <p className="error" data-testid={`error-${field}`} id={`error-${field}`} role="alert">
        {errors[field]}
      </p>
    ) : null;

  return (
    <form noValidate onSubmit={onSubmit}>
      <fieldset>
        <legend>Registration type</legend>
        <label>
          <input
            type="radio"
            name="type"
            data-testid="type-external"
            checked={type === "EXTERNAL"}
            onChange={() => chooseType("EXTERNAL")}
          />{" "}
          External participant
        </label>
        <label>
          <input
            type="radio"
            name="type"
            data-testid="type-student"
            checked={type === "STUDENT"}
            onChange={() => chooseType("STUDENT")}
          />{" "}
          Student
        </label>
      </fieldset>

      <fieldset>
        <legend>Your details</legend>
        {FIELDS[type].map(({ name }) => (
          <div className="field" key={name}>
            <label htmlFor={`field-${name}`}>{LABELS[name]}</label>
            <input
              id={`field-${name}`}
              data-testid={`field-${name}`}
              type={name === "email" ? "email" : "text"}
              required
              aria-invalid={errors[name] ? true : undefined}
              aria-describedby={errors[name] ? `error-${name}` : undefined}
              value={values[name] ?? ""}
              onChange={(e) => setValues({ ...values, [name]: e.target.value })}
            />
            {error(name)}
          </div>
        ))}
      </fieldset>

      {CATEGORY_ORDER.map((category) => {
        const options = offered.filter((o) => o.category === category);
        if (options.length === 0) {
          return null;
        }
        const max = config.categories.find((c) => c.category === category)?.maxSelections;
        return (
          <fieldset key={category} data-testid={`options-${category}`}>
            <legend>
              {HEADINGS[category]}
              {max !== undefined ? ` (up to ${max})` : ""}
            </legend>
            {options.map((option) => (
              <label key={option.id} className="choice">
                <input
                  type="checkbox"
                  data-testid={`option-${option.id}`}
                  checked={optionIds.includes(option.id)}
                  onChange={(e) => setOptionIds(toggle(optionIds, option.id, e.target.checked))}
                />{" "}
                {option.name}
              </label>
            ))}
          </fieldset>
        );
      })}
      {error("optionIds")}

      <fieldset>
        <legend>Consent</legend>
        {config.consents.map((consent) => (
          <label key={consent.id} className="choice">
            <input
              type="checkbox"
              data-testid={`consent-${consent.id}`}
              required={consent.mandatory}
              checked={consentIds.includes(consent.id)}
              onChange={(e) => setConsentIds(toggle(consentIds, consent.id, e.target.checked))}
            />{" "}
            {consent.text}
          </label>
        ))}
        {error("consentIds")}
      </fieldset>

      <Recaptcha
        siteKey={config.recaptcha.siteKey}
        testMode={config.recaptcha.testMode}
        token={token}
        resetKey={resetKey}
        onToken={onToken}
      />
      {error("recaptchaToken")}

      {formError ? (
        <p className="error" data-testid="form-error" role="alert">
          {formError}
        </p>
      ) : null}

      <button type="submit" data-testid="submit" disabled={submitting}>
        Register
      </button>
    </form>
  );
}
