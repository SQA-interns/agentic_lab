import { useState, type FormEvent } from "react";
import { submitRegistration } from "../api";
import { messages } from "../messages";
import type {
  RegistrationCreated,
  RegistrationFormData,
  RegistrationRequest,
  RegistrationType,
  TextFieldName,
} from "../types";
import { fieldsFor, validateDraft, type Draft } from "../validation";
import { Captcha } from "./Captcha";
import { OptionsFieldset } from "./OptionsFieldset";
import { TextField } from "./TextField";

interface RegistrationFormProps {
  form: RegistrationFormData;
  onRegistered: (created: RegistrationCreated, firstName: string, lastName: string) => void;
}

const LIST_FIELDS = ["optionIds", "consentIds", "captchaToken"];

/** The registration form of docs/02_contracts/ui-form.json. */
export function RegistrationForm({ form, onRegistered }: RegistrationFormProps) {
  const [type, setType] = useState<RegistrationType | null>(null);
  const [values, setValues] = useState<Partial<Record<TextFieldName, string>>>({});
  const [optionIds, setOptionIds] = useState<string[]>([]);
  const [consentIds, setConsentIds] = useState<string[]>([]);
  const [captchaToken, setCaptchaToken] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const categories =
    type === null
      ? []
      : form.categories
          .map((category) => ({
            ...category,
            options: category.options.filter((option) => option.availableTo.includes(type)),
          }))
          .filter((category) => category.options.length > 0);

  function chooseType(next: RegistrationType) {
    setType(next);
    const available = form.categories
      .flatMap((category) => category.options)
      .filter((option) => option.availableTo.includes(next))
      .map((option) => option.id);
    setOptionIds((selected) => selected.filter((id) => available.includes(id)));
    setErrors({});
    setFormError(null);
  }

  function toggle(list: string[], id: string): string[] {
    return list.includes(id) ? list.filter((item) => item !== id) : [...list, id];
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (type === null || submitting) {
      return;
    }
    const fields = fieldsFor(type);
    const draft: Draft = {
      type,
      values: Object.fromEntries(fields.map((field) => [field, values[field] ?? ""])),
      optionIds: categories
        .flatMap((category) => category.options)
        .map((option) => option.id)
        .filter((id) => optionIds.includes(id)),
      consentIds: form.consents
        .map((consent) => consent.id)
        .filter((id) => consentIds.includes(id)),
      captchaToken,
    };
    const clientErrors = validateDraft(draft, categories, form.consents);
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }
    const request: RegistrationRequest = {
      type,
      ...Object.fromEntries(fields.map((field) => [field, (values[field] ?? "").trim()])),
      optionIds: draft.optionIds,
      consentIds: draft.consentIds,
      captchaToken,
    };
    setSubmitting(true);
    const result = await submitRegistration(request);
    setSubmitting(false);
    if (result.kind === "created") {
      onRegistered(result.created, request.firstName ?? "", request.lastName ?? "");
      return;
    }
    setCaptchaReset((count) => count + 1);
    if (result.kind === "rejected") {
      const known: string[] = [...fields, ...LIST_FIELDS];
      const fieldErrors: Record<string, string> = {};
      const other: string[] = [];
      for (const error of result.error.fieldErrors) {
        if (known.includes(error.field)) {
          fieldErrors[error.field] ??= error.message;
        } else {
          other.push(error.message);
        }
      }
      setErrors(fieldErrors);
      setFormError(other.length > 0 ? other.join(" ") : null);
      return;
    }
    setFormError(
      (result.errorCode !== null ? messages.serverErrors[result.errorCode] : undefined) ??
        messages.genericError,
    );
  }

  return (
    <form data-testid="registration-form" noValidate onSubmit={submit} className="registration">
      <fieldset className="types">
        <legend>{messages.typeLegend}</legend>
        {(["EXTERNAL", "STUDENT"] as const).map((option) => (
          <label key={option} className="choice">
            <input
              type="radio"
              name="type"
              value={option}
              data-testid={option === "EXTERNAL" ? "type-external" : "type-student"}
              checked={type === option}
              onChange={() => chooseType(option)}
            />
            {messages.types[option]}
          </label>
        ))}
      </fieldset>

      {type !== null && (
        <>
          {fieldsFor(type).map((field) => (
            <TextField
              key={field}
              name={field}
              value={values[field] ?? ""}
              error={errors[field]}
              onChange={(value) => setValues((current) => ({ ...current, [field]: value }))}
            />
          ))}

          {categories.map((category) => (
            <OptionsFieldset
              key={category.category}
              category={category.category}
              options={category.options}
              selected={optionIds}
              invalid={errors["optionIds"] !== undefined}
              onToggle={(id) => setOptionIds((current) => toggle(current, id))}
            />
          ))}
          <FieldErrorText field="optionIds" errors={errors} />

          <fieldset className="consents">
            <legend>{messages.consentsLegend}</legend>
            {form.consents.map((consent) => (
              <label key={consent.id} className="choice">
                <input
                  type="checkbox"
                  data-testid={`consent-${consent.id}`}
                  checked={consentIds.includes(consent.id)}
                  aria-describedby={
                    errors["consentIds"] !== undefined ? "consentIds-error" : undefined
                  }
                  onChange={() => setConsentIds((current) => toggle(current, consent.id))}
                />
                {consent.text}
              </label>
            ))}
          </fieldset>
          <FieldErrorText field="consentIds" errors={errors} />

          <Captcha
            mode={form.captcha.mode}
            siteKey={form.captcha.siteKey}
            token={captchaToken}
            resetSignal={captchaReset}
            invalid={errors["captchaToken"] !== undefined}
            onToken={setCaptchaToken}
          />
          <FieldErrorText field="captchaToken" errors={errors} />

          {formError !== null && (
            <div data-testid="form-error" role="alert" className="error form-error">
              {formError}
            </div>
          )}

          <button type="submit" data-testid="submit" disabled={submitting}>
            {submitting ? messages.submitting : messages.submit}
          </button>
        </>
      )}
    </form>
  );
}

function FieldErrorText({ field, errors }: { field: string; errors: Record<string, string> }) {
  const error = errors[field];
  if (error === undefined) {
    return null;
  }
  return (
    <p id={`${field}-error`} data-testid={`error-${field}`} role="alert" className="error">
      {error}
    </p>
  );
}
