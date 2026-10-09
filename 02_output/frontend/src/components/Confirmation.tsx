import { messages } from "../messages";

interface ConfirmationProps {
  firstName: string;
  lastName: string;
  registrationId: string;
}

/** Shown only after the backend accepted and stored the registration (BR-06). */
export function Confirmation({ firstName, lastName, registrationId }: ConfirmationProps) {
  return (
    <section data-testid="confirmation" role="status" className="confirmation">
      <p>{messages.confirmation(firstName, lastName)}</p>
      <p>
        {messages.registrationId}: <code>{registrationId}</code>
      </p>
    </section>
  );
}
