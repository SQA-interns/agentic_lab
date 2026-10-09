import { messages } from "../messages";
import type { TextFieldName } from "../types";

interface TextFieldProps {
  name: TextFieldName;
  value: string;
  error: string | undefined;
  onChange: (value: string) => void;
}

const AUTOCOMPLETE: Partial<Record<TextFieldName, string>> = {
  firstName: "given-name",
  lastName: "family-name",
  email: "email",
  organization: "organization",
};

/** One labelled participant field with its error shown next to it (NFR-03). */
export function TextField({ name, value, error, onChange }: TextFieldProps) {
  const id = `field-${name}`;
  const errorId = `${name}-error`;
  return (
    <div className="field">
      <label htmlFor={id}>{messages.fields[name]}</label>
      <input
        id={id}
        data-testid={id}
        name={name}
        type={name === "email" ? "email" : "text"}
        value={value}
        autoComplete={AUTOCOMPLETE[name] ?? "off"}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? errorId : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error !== undefined && (
        <p id={errorId} data-testid={`error-${name}`} role="alert" className="error">
          {error}
        </p>
      )}
    </div>
  );
}
