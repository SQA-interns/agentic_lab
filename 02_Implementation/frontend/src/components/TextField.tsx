import type { FieldDef } from '../types';

interface TextFieldProps {
  field: FieldDef;
  value: string;
  error: string | undefined;
  disabled: boolean;
  onChange: (value: string) => void;
  onBlur: () => void;
}

export function TextField({ field, value, error, disabled, onChange, onBlur }: TextFieldProps) {
  const id = `field-${field.name}`;
  const errorId = `${id}-error`;
  return (
    <div className="field">
      <label htmlFor={id}>
        {field.label} <span aria-hidden="true">*</span>
      </label>
      <input
        id={id}
        name={field.name}
        type={field.kind === 'email' ? 'email' : 'text'}
        inputMode={field.kind === 'email' ? 'email' : undefined}
        autoComplete={field.autoComplete}
        value={value}
        required
        disabled={disabled}
        aria-invalid={error !== undefined}
        aria-describedby={error !== undefined ? errorId : undefined}
        onChange={(e) => {
          onChange(e.target.value);
        }}
        onBlur={onBlur}
      />
      {error !== undefined && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
