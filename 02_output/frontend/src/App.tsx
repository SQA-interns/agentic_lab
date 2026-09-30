import { useEffect, useState } from "react";
import { fetchConfig, fetchOptions } from "./api";
import { RegistrationForm } from "./RegistrationForm";
import type { ClientConfig, OptionsResponse, Registration } from "./types";

type Loaded = { config: ClientConfig; options: OptionsResponse };

export function App() {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState(false);
  const [registration, setRegistration] = useState<Registration | null>(null);

  useEffect(() => {
    Promise.all([fetchConfig(), fetchOptions()])
      .then(([config, options]) => setLoaded({ config, options }))
      .catch(() => setFailed(true));
  }, []);

  if (failed) {
    return (
      <main>
        <h1>Registration</h1>
        <p role="alert">
          The registration form is not available right now. Please try again later.
        </p>
      </main>
    );
  }
  if (!loaded) {
    return (
      <main>
        <h1>Registration</h1>
        <p>Loading…</p>
      </main>
    );
  }
  return (
    <main>
      <h1>Registration: {loaded.config.conferenceName}</h1>
      {registration ? (
        <p role="status">Registration received.</p>
      ) : (
        <RegistrationForm
          config={loaded.config}
          options={loaded.options.options}
          consents={loaded.options.consents}
          onRegistered={setRegistration}
        />
      )}
    </main>
  );
}
