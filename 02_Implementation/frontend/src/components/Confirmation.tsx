import type { RegistrationResponse } from "../api/client";
import { CATEGORY_HEADINGS } from "./OptionGroups";

/** Shown only after a 201 response (US-004, specification §12). */
export function Confirmation({
  registration,
}: {
  registration: RegistrationResponse;
}) {
  return (
    <section className="confirmation" aria-live="polite">
      <h2>Registration received</h2>
      <p>
        Thank you. Your registration has been stored and a confirmation email is
        on its way.
      </p>
      <dl>
        <dt>Name</dt>
        <dd>
          {registration.firstName} {registration.lastName}
        </dd>
        <dt>Email</dt>
        <dd>{registration.email}</dd>
        <dt>Registration ID</dt>
        <dd>{registration.registrationId}</dd>
        <dt>Selected options</dt>
        <dd>
          {registration.options.length === 0 ? (
            "No optional activities selected."
          ) : (
            <ul>
              {registration.options.map((o) => (
                <li key={o.id}>
                  {o.name}{" "}
                  <span className="muted">
                    ({CATEGORY_HEADINGS[o.category]})
                  </span>
                </li>
              ))}
            </ul>
          )}
        </dd>
      </dl>
    </section>
  );
}
