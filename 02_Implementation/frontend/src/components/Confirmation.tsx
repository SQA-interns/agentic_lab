import { TYPE_LABELS } from "../fields";
import type { RegistrationType } from "../types";

interface ConfirmationProps {
  type: RegistrationType;
  registrationId: string;
}

export function Confirmation({ type, registrationId }: ConfirmationProps) {
  return (
    <section className="confirmation" role="status" aria-live="polite">
      <h2>Registration received</h2>
      <p>Thank you, your registration has been received.</p>
      <p>
        Registration type: <strong>{TYPE_LABELS[type]}</strong>
        <br />
        Registration ID: <code>{registrationId}</code>
      </p>
      <p>
        A confirmation email is being sent to the email address you provided.
      </p>
    </section>
  );
}
