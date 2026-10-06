// Registration form for both types (US-001, US-002), names of docs/02_contracts/ui-registration-form.json.
import { useCallback, useState, type FormEvent } from "react";
import {
  submitRegistration,
  type Confirmation,
  type FormConfig,
  type OptionCategory,
  type RegistrationRequest,
  type RegistrationType,
} from "./api";
import { Captcha } from "./Captcha";
import { FIELDS, LABELS, validate } from "./validation";

const GROUPS: { category: OptionCategory; legend: string }[] = [
  { category: "workshop", legend: "Workshops" },
  { category: "event", legend: "Events" },
  { category: "meal", legend: "Meals" },
  { category: "other", legend: "Other activities" },
];

const STATUS_MESSAGES: Record<number, string> = {
  429: "Too many requests. Please wait a moment and try again.",
  503: "The anti-automation check could not be completed. Please try again later.",
};
const FAILED = "Your registration was not saved. Please try again later.";

interface Props {
  config: FormConfig;
  onAccepted: (confirmation: Confirmation) => void;
}

export function RegistrationForm({ config, onAccepted }: Props) {
  const [type, setType] = useState<RegistrationType>("external");
  const [values, setValues] = useState<Record<string, string>>({});
  const [options, setOptions] = useState<Set<string>>(new Set());
  const [consents, setConsents] = useState<Set<string>>(new Set());
  const [captchaToken, setCaptchaToken] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [general, setGeneral] = useState("");
  const [sending, setSending] = useState(false);
  const onToken = useCallback((token: string) => setCaptchaToken(token), []);

  const offered = config.options.filter((o) => o.offeredTo.includes(type));

  function toggle(set: Set<string>, id: string): Set<string> {
    const next = new Set(set);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    return next;
  }

  function chooseType(next: RegistrationType) {
    setType(next);
    setErrors({});
    setOptions(
      (current) =>
        new Set(
          [...current].filter((id) =>
            config.options.some(
              (o) => o.id === id && o.offeredTo.includes(next),
            ),
          ),
        ),
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setGeneral("");
    const found = validate(
      type,
      values,
      config.consents,
      consents,
      captchaToken,
    );
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    const request: RegistrationRequest = {
      type,
      firstName: values.firstName ?? "",
      lastName: values.lastName ?? "",
      email: values.email ?? "",
      optionIds: offered.filter((o) => options.has(o.id)).map((o) => o.id),
      consents: config.consents
        .filter((c) => consents.has(c.id))
        .map((c) => c.id),
      captchaToken,
    };
    for (const field of FIELDS[type].slice(3)) {
      (request as unknown as Record<string, string>)[field] =
        values[field] ?? "";
    }
    setSending(true);
    try {
      const result = await submitRegistration(request);
      if (result.kind === "accepted") {
        onAccepted(result.confirmation);
        return;
      }
      const byField: Record<string, string> = {};
      const other: string[] = [];
      for (const error of result.errors) {
        if (
          FIELDS[type].includes(error.field) ||
          error.field.startsWith("consents.") ||
          error.field === "captchaToken"
        ) {
          byField[error.field] = error.message;
        } else {
          other.push(error.message);
        }
      }
      setErrors(byField);
      if (other.length > 0) {
        setGeneral(other.join(" "));
      } else if (result.errors.length === 0) {
        setGeneral(STATUS_MESSAGES[result.status] ?? FAILED);
      }
    } catch {
      setGeneral(FAILED);
    } finally {
      setSending(false);
    }
  }

  return (
    <form noValidate onSubmit={onSubmit}>
      <div
        role="radiogroup"
        aria-label="Registration type"
        className="type-selector"
      >
        {(["external", "student"] as const).map((value) => (
          <label key={value}>
            <input
              type="radio"
              name="type"
              value={value}
              checked={type === value}
              onChange={() => chooseType(value)}
            />{" "}
            {value === "external" ? "External participant" : "Student"}
          </label>
        ))}
      </div>

      {FIELDS[type].map((field) => (
        <div key={field} className="field">
          <label htmlFor={field}>{LABELS[field]}</label>
          <input
            id={field}
            name={field}
            type={field === "email" ? "email" : "text"}
            value={values[field] ?? ""}
            aria-invalid={errors[field] ? true : undefined}
            aria-describedby={errors[field] ? `${field}-error` : undefined}
            onChange={(e) => setValues({ ...values, [field]: e.target.value })}
          />
          {errors[field] && (
            <p id={`${field}-error`} role="alert" className="field-error">
              {errors[field]}
            </p>
          )}
        </div>
      ))}

      {GROUPS.map(({ category, legend }) => {
        const inGroup = offered.filter((o) => o.category === category);
        if (inGroup.length === 0) {
          return null;
        }
        return (
          <fieldset key={category}>
            <legend>{legend}</legend>
            {inGroup.map((option) => (
              <label key={option.id} className="option">
                <input
                  type="checkbox"
                  checked={options.has(option.id)}
                  onChange={() => setOptions(toggle(options, option.id))}
                />{" "}
                {option.name}
              </label>
            ))}
          </fieldset>
        );
      })}

      <div className="consents">
        {config.consents.map((consent) => {
          const key = `consents.${consent.id}`;
          const errorId = `consent-${consent.id}-error`;
          return (
            <div key={consent.id} className="consent">
              <label>
                <input
                  type="checkbox"
                  checked={consents.has(consent.id)}
                  aria-invalid={errors[key] ? true : undefined}
                  aria-describedby={errors[key] ? errorId : undefined}
                  onChange={() => setConsents(toggle(consents, consent.id))}
                />{" "}
                {consent.text}
              </label>
              {errors[key] && (
                <p id={errorId} role="alert" className="field-error">
                  {errors[key]}
                </p>
              )}
            </div>
          );
        })}
      </div>

      <Captcha
        mode={config.captcha.mode}
        siteKey={config.captcha.siteKey}
        token={captchaToken}
        onToken={onToken}
        error={errors.captchaToken}
      />

      {general && (
        <p role="alert" className="general-error">
          {general}
        </p>
      )}
      <button type="submit" disabled={sending}>
        Register
      </button>
    </form>
  );
}
