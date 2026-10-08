import { useEffect, useState } from "react";
import { loadForm, type FormConfig } from "./api";
import { RegistrationForm } from "./RegistrationForm";

const UNAVAILABLE = "The registration form is not available right now. Please try again later.";

export function App() {
  const [config, setConfig] = useState<FormConfig | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [accepted, setAccepted] = useState(false);

  useEffect(() => {
    let active = true;
    loadForm()
      .then((form) => active && setConfig(form))
      .catch(() => active && setLoadError(true));
    return () => {
      active = false;
    };
  }, []);

  return (
    <main data-testid="registration-page">
      <h1>{config ? config.conferenceName : "Conference"} registration</h1>
      {loadError ? (
        <p className="error" data-testid="form-error" role="alert">
          {UNAVAILABLE}
        </p>
      ) : null}
      {accepted ? (
        <p className="confirmation" data-testid="confirmation" role="status">
          Thank you, your registration was received.
        </p>
      ) : config ? (
        <RegistrationForm config={config} onAccepted={() => setAccepted(true)} />
      ) : null}
    </main>
  );
}
