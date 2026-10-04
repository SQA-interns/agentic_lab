import { useEffect, useState } from 'react';
import { fetchFormConfig } from './api';
import { message, type FormConfig } from './contract';
import { RegistrationForm, type Accepted } from './RegistrationForm';

// The registration page: the form, or the confirmation once the backend accepted (BR-06).
export function App() {
  const [config, setConfig] = useState<FormConfig | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [accepted, setAccepted] = useState<Accepted | null>(null);

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

  let content;
  if (accepted) {
    content = (
      <section role="status" className="confirmation">
        <h2>Registration received</h2>
        <p>
          Thank you, {accepted.firstName}. Your registration was received. A confirmation email is
          on its way to {accepted.email}.
        </p>
      </section>
    );
  } else if (loadFailed) {
    content = <p role="alert">{message('GENERIC')}</p>;
  } else if (!config) {
    content = <p>Loading…</p>;
  } else {
    content = <RegistrationForm config={config} onAccepted={setAccepted} />;
  }

  return (
    <main>
      <h1>Conference registration</h1>
      {config && !accepted && <p className="conference">{config.conferenceName}</p>}
      {content}
    </main>
  );
}
