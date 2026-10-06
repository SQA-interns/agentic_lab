import { useEffect, useState } from "react";
import {
  fetchFormConfig,
  type Confirmation as ConfirmationData,
  type FormConfig,
} from "./api";
import { Confirmation } from "./Confirmation";
import { RegistrationForm } from "./RegistrationForm";

export function App() {
  const [config, setConfig] = useState<FormConfig | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [confirmation, setConfirmation] = useState<ConfirmationData | null>(
    null,
  );

  useEffect(() => {
    let active = true;
    fetchFormConfig()
      .then((loaded) => {
        if (active) {
          setConfig(loaded);
        }
      })
      .catch(() => {
        if (active) {
          setLoadFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <main>
      <h1>Conference registration</h1>
      {config && <p className="conference-name">{config.conferenceName}</p>}
      {confirmation ? (
        <Confirmation data={confirmation} />
      ) : config ? (
        <RegistrationForm config={config} onAccepted={setConfirmation} />
      ) : loadFailed ? (
        <p role="alert">
          The registration form could not be loaded. Please try again later.
        </p>
      ) : (
        <p>Loading…</p>
      )}
    </main>
  );
}
