import type { RegistrationAccepted } from "./api";
import { texts } from "./texts";

/** Shown in place of the form, only after the backend accepted the registration (BR-06). */
export function Confirmation({ registration }: { registration: RegistrationAccepted }) {
  return (
    <section role="status" className="confirmation">
      <h2>{texts.confirmationHeading}</h2>
      <p>{texts.confirmationText(registration.firstName, registration.email)}</p>
    </section>
  );
}
