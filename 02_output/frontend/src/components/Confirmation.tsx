import { MESSAGES } from "../messages";

interface Props {
  conferenceName: string;
  registrationId: string;
}

/** Shown only after the backend accepted the registration (BR-06). */
export function Confirmation({ conferenceName, registrationId }: Props) {
  return (
    <section data-testid="confirmation" className="confirmation" role="status">
      <h2>{MESSAGES.CONFIRMATION}</h2>
      <p>
        A confirmation email for {conferenceName} is on its way. Your registration ID is{" "}
        <code>{registrationId}</code>.
      </p>
    </section>
  );
}
