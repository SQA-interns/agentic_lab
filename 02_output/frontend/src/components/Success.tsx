import { useEffect, useRef } from 'react';
import type { Accepted } from '../api';

/** Shown only after the backend accepted the registration (BR-05); claims no mail delivery. */
export function Success({ accepted }: { accepted: Accepted }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus();
  }, []);
  return (
    <main>
      <h1 tabIndex={-1} ref={heading}>
        Registration received
      </h1>
      <p>
        Your registration ID:{' '}
        <strong data-testid="registration-id">{accepted.registrationId}</strong>
      </p>
      <p>
        Your registration has been stored. A confirmation email will be sent to the address you
        entered; please keep the registration ID for your records.
      </p>
      <p>
        <a href="/">Back to the start page</a>
      </p>
    </main>
  );
}
