import type { Registration } from "./types";

// Shown only after the API accepted the registration (BR-06, docs/02_contracts/ui.md).

export function Confirmation({ registration }: { registration: Registration }) {
  return (
    <section aria-labelledby="confirmation-heading">
      <h2 id="confirmation-heading">Registration received</h2>
      <p>
        Thank you, {registration.firstName} {registration.lastName}. Your registration ID is{" "}
        {registration.id}.
      </p>
      <h3 id="selected-options-heading">Selected options</h3>
      {registration.options.length === 0 ? (
        <p>No options selected.</p>
      ) : (
        <ul aria-labelledby="selected-options-heading">
          {registration.options.map((o) => (
            <li key={o.id}>{o.name}</li>
          ))}
        </ul>
      )}
      <p>A confirmation email has been sent to {registration.email}.</p>
    </section>
  );
}
