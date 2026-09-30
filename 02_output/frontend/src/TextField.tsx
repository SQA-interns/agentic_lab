import type { FieldDefinition } from "./fields";

// A labelled text input with its error message directly after it (NFR-03).

type Props = {
  field: FieldDefinition;
  value: string;
  error?: string;
  onChange: (value: string) => void;
};

export function TextField({ field, value, error, onChange }: Props) {
  const errorId = `${field.name}-error`;
  return (
    <div className="field">
      <label htmlFor={field.name}>{field.label}</label>
      <input
        id={field.name}
        name={field.name}
        type={field.inputType ?? "text"}
        value={value}
        maxLength={field.maxLength}
        autoComplete={field.autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {error && (
        <p id={errorId} className="error">
          {error}
        </p>
      )}
    </div>
  );
}
