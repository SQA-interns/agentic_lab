import type { Consent } from "./types";

// Consent checkboxes, never pre-checked (BR-05, SB-14).

type Props = {
  consents: Consent[];
  given: string[];
  onToggle: (id: string) => void;
  error?: string;
};

export function ConsentList({ consents, given, onToggle, error }: Props) {
  return (
    <fieldset aria-describedby={error ? "consents-error" : undefined}>
      <legend>Consents</legend>
      {consents.map((c) => (
        <div key={c.id} className="choice">
          <input
            type="checkbox"
            id={`consent-${c.id}`}
            checked={given.includes(c.id)}
            onChange={() => onToggle(c.id)}
          />
          <label htmlFor={`consent-${c.id}`}>
            {c.text}
            {c.required ? " (required)" : ""}
          </label>
        </div>
      ))}
      {error && (
        <p id="consents-error" className="error">
          {error}
        </p>
      )}
    </fieldset>
  );
}
