import type { Accepted } from "./api";
import { categoryHeadings } from "./messages";

/** Shown only after the backend accepted the registration (BR-06, US-004). */
export function Confirmation({ registration }: { registration: Accepted }) {
  return (
    <section
      className="confirmation"
      data-testid="confirmation"
      aria-live="polite"
    >
      <h2>Registration received</h2>
      <p data-testid="confirmation-name">
        Thank you, {registration.firstName} {registration.lastName}. Your
        registration was received and a confirmation email is on its way.
      </p>
      {registration.selectedOptions.length > 0 && (
        <ul data-testid="confirmation-options">
          {registration.selectedOptions.map((o) => (
            <li key={o.id}>
              {categoryHeadings[o.category]}: {o.name}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
