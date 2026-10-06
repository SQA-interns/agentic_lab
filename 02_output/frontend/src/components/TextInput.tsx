import type { TextField } from "../validation/rules";
import { LABELS, message } from "../messages";

interface Props {
  field: TextField;
  value: string;
  error?: string;
  type?: "text" | "email";
  autoComplete?: string;
  onChange: (field: TextField, value: string) => void;
  onBlur: (field: TextField) => void;
}

export function TextInput({
  field,
  value,
  error,
  type = "text",
  autoComplete,
  onChange,
  onBlur,
}: Props) {
  const id = `field-${field}`;
  const errorId = `error-${field}`;
  return (
    <div className="field">
      <label htmlFor={id}>{LABELS[field]}</label>
      <input
        id={id}
        data-testid={field}
        name={field}
        type={type}
        value={value}
        autoComplete={autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={(e) => onChange(field, e.target.value)}
        onBlur={() => onBlur(field)}
      />
      {error && (
        <p id={errorId} data-testid={errorId} className="error">
          {message(error)}
        </p>
      )}
    </div>
  );
}
