// Confirmation shown only after the backend accepted the registration (US-004, BR-06).
import type { Confirmation as ConfirmationData } from "./api";

export function Confirmation({ data }: { data: ConfirmationData }) {
  return (
    <section role="status" className="confirmation">
      <h2>Registration received</h2>
      <p>
        Thank you, {data.firstName} {data.lastName}. A confirmation will be sent
        to {data.email}.
      </p>
      {data.options.length > 0 ? (
        <>
          <p>Selected options:</p>
          <ul>
            {data.options.map((option) => (
              <li key={option.id}>{option.name}</li>
            ))}
          </ul>
        </>
      ) : (
        <p>No options selected.</p>
      )}
      <p>Registration ID: {data.registrationId}</p>
    </section>
  );
}
