import { useEffect, useState } from "react";
import {
  fetchFormConfig,
  type FormConfig,
  type RegistrationResponse,
  type RegistrationType,
} from "./api";
import { Confirmation } from "./components/Confirmation";
import { RegistrationForm } from "./components/RegistrationForm";

const TYPE_LABELS: Record<RegistrationType, string> = {
  EXTERNAL: "External participant",
  STUDENT: "Student",
};

export function App() {
  const [config, setConfig] = useState<FormConfig | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [type, setType] = useState<RegistrationType>("EXTERNAL");
  const [registration, setRegistration] = useState<RegistrationResponse | null>(
    null,
  );
  const [formKey, setFormKey] = useState(0);

  useEffect(() => {
    fetchFormConfig()
      .then(setConfig)
      .catch(() => setLoadFailed(true));
  }, []);

  function startNewRegistration() {
    setRegistration(null);
    setFormKey((k) => k + 1);
  }

  return (
    <main>
      <h1>Conference registration</h1>
      {loadFailed && (
        <p className="form-error" role="alert">
          The registration form is currently unavailable. Please try again
          later.
        </p>
      )}
      {!loadFailed && !config && <p>Loading…</p>}
      {config && registration && (
        <Confirmation
          registration={registration}
          onNewRegistration={startNewRegistration}
        />
      )}
      {config && !registration && (
        <>
          <div
            className="type-switch"
            role="radiogroup"
            aria-label="Registration type"
          >
            {(Object.keys(TYPE_LABELS) as RegistrationType[]).map((t) => (
              <label key={t} className="checkbox">
                <input
                  type="radio"
                  name="registrationType"
                  value={t}
                  checked={type === t}
                  onChange={() => setType(t)}
                />
                {TYPE_LABELS[t]}
              </label>
            ))}
          </div>
          <h2>{TYPE_LABELS[type]} registration</h2>
          <RegistrationForm
            key={`${type}-${formKey}`}
            type={type}
            config={config}
            onRegistered={setRegistration}
          />
        </>
      )}
    </main>
  );
}
