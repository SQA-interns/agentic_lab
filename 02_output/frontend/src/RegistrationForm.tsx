import { useCallback, useState, type FormEvent } from "react";
import { submitRegistration } from "./api";
import { ConsentList } from "./ConsentList";
import { EMPTY_VALUES, FIELDS, type FieldName, type FieldValues } from "./fields";
import { OptionGroups } from "./OptionGroups";
import { Recaptcha } from "./Recaptcha";
import { TextField } from "./TextField";
import type {
  ClientConfig,
  ConferenceOption,
  Consent,
  FieldErrors,
  Registration,
  RegistrationType,
} from "./types";
import { MESSAGES, toRequestBody, validate } from "./validation";

// The registration form (US-001, US-002, docs/02_contracts/ui.md).

type Props = {
  config: ClientConfig;
  options: ConferenceOption[];
  consents: Consent[];
  onRegistered: (registration: Registration) => void;
};

function toggle(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
}

export function RegistrationForm({ config, options, consents, onRegistered }: Props) {
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [values, setValues] = useState<FieldValues>(EMPTY_VALUES);
  const [optionIds, setOptionIds] = useState<string[]>([]);
  const [given, setGiven] = useState<string[]>([]);
  const [token, setToken] = useState("");
  const [resetCount, setResetCount] = useState(0);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [alert, setAlert] = useState<string | undefined>();
  const [submitting, setSubmitting] = useState(false);

  const onToken = useCallback((t: string) => setToken(t), []);

  const setValue = (name: FieldName, value: string) => setValues((v) => ({ ...v, [name]: value }));

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAlert(undefined);
    const found = validate(type, values, consents, given);
    if (!token) {
      found.recaptchaToken = MESSAGES.recaptcha;
    }
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    setSubmitting(true);
    const ordered = options.map((o) => o.id).filter((id) => optionIds.includes(id));
    const result = await submitRegistration(toRequestBody(type, values, ordered, given, token));
    setSubmitting(false);
    if (result.ok) {
      onRegistered(result.registration);
      return;
    }
    setErrors(result.fieldErrors);
    setAlert(result.message);
    setResetCount((n) => n + 1);
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <fieldset>
        <legend>Registration type</legend>
        <div className="choice">
          <input
            type="radio"
            id="type-external"
            name="type"
            checked={type === "EXTERNAL"}
            onChange={() => setType("EXTERNAL")}
          />
          <label htmlFor="type-external">External participant</label>
        </div>
        <div className="choice">
          <input
            type="radio"
            id="type-student"
            name="type"
            checked={type === "STUDENT"}
            onChange={() => setType("STUDENT")}
          />
          <label htmlFor="type-student">Student</label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Your details</legend>
        {FIELDS[type].map((field) => (
          <TextField
            key={field.name}
            field={field}
            value={values[field.name]}
            error={errors[field.name]}
            onChange={(v) => setValue(field.name, v)}
          />
        ))}
      </fieldset>

      <OptionGroups
        options={options}
        selected={optionIds}
        onToggle={(id) => setOptionIds((l) => toggle(l, id))}
        error={errors.optionIds}
      />

      <ConsentList
        consents={consents}
        given={given}
        onToggle={(id) => setGiven((l) => toggle(l, id))}
        error={errors.consents}
      />

      <Recaptcha
        siteKey={config.recaptchaSiteKey}
        testMode={config.recaptchaTestMode}
        resetCount={resetCount}
        onToken={onToken}
        error={errors.recaptchaToken}
      />

      {alert && (
        <p role="alert" className="alert">
          {alert}
        </p>
      )}

      <button type="submit" disabled={submitting}>
        {submitting ? "Submitting…" : "Register"}
      </button>
    </form>
  );
}
