import { useEffect, useRef } from 'react';
import type { RegistrationResponse } from '../types';

interface ConfirmationProps {
  result: RegistrationResponse;
  firstName: string;
  email: string;
}

/** Shown only after the backend confirmed the registration with 201 (US-004). */
export function Confirmation({ result, firstName, email }: ConfirmationProps) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <section className="confirmation" aria-live="polite">
      <h2 ref={headingRef} tabIndex={-1}>
        Registration received
      </h2>
      <p>Thank you, {firstName}. Your registration has been successfully processed.</p>
      <p>
        Registration ID: <strong className="reg-id">{result.id}</strong>
      </p>
      <p>A confirmation email will be sent to {email}.</p>
      <p>
        <a href="/">Back to the start page</a>
      </p>
    </section>
  );
}
