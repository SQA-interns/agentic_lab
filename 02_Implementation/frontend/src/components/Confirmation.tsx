import type { RegistrationResponse } from "../api";

interface ConfirmationProps {
  registration: RegistrationResponse;
  onNewRegistration: () => void;
}

/** Shown only after the backend accepted the registration (HTTP 201). */
export function Confirmation({
  registration,
  onNewRegistration,
}: ConfirmationProps) {
  return (
    <section className="confirmation" role="status" aria-live="polite">
      <h2>Registration received</h2>
      <p>
        Thank you! Your{" "}
        {registration.registrationType === "STUDENT"
          ? "student"
          : "external participant"}{" "}
        registration has been received successfully.
      </p>
      <p>
        Registration reference:{" "}
        <strong data-testid="registration-id">
          {registration.registrationId}
        </strong>
      </p>
      <p>A confirmation email is being sent to the address you provided.</p>
      <button type="button" onClick={onNewRegistration}>
        Register another participant
      </button>
    </section>
  );
}
