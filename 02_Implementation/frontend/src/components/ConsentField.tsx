interface ConsentFieldProps {
  checked: boolean;
  error: string | undefined;
  disabled: boolean;
  onChange: (checked: boolean) => void;
}

/** Mandatory privacy consent; never preselected (FORM_SCHEMA §Consent). */
export function ConsentField({ checked, error, disabled, onChange }: ConsentFieldProps) {
  const invalid = error !== undefined;
  return (
    <div className="field consent">
      <label className="checkbox">
        <input
          type="checkbox"
          name="privacyConsent"
          checked={checked}
          disabled={disabled}
          aria-invalid={invalid}
          aria-describedby={invalid ? 'consent-error' : undefined}
          onChange={(e) => {
            onChange(e.target.checked);
          }}
        />
        <span>
          I agree that my personal data is processed for the purpose of organising the conference
          and my registration. <span aria-hidden="true">*</span>
        </span>
      </label>
      {invalid && (
        <p id="consent-error" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
