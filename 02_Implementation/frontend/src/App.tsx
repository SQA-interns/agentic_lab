import { useEffect, useState } from 'react';
import { fetchFormConfig } from './api';
import { RegistrationForm } from './RegistrationForm';
import type { FormConfig, ParticipantType } from './types';

const TABS: { type: ParticipantType; label: string }[] = [
  { type: 'external', label: 'External participant' },
  { type: 'student', label: 'Student' },
];

function initialTab(): ParticipantType {
  return window.location.hash === '#student' ? 'student' : 'external';
}

export function App() {
  const [config, setConfig] = useState<FormConfig | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [tab, setTab] = useState<ParticipantType>(initialTab);

  useEffect(() => {
    const controller = new AbortController();
    fetchFormConfig(controller.signal)
      .then(setConfig)
      .catch((e: unknown) => {
        if (!(e instanceof DOMException && e.name === 'AbortError')) {
          setLoadError(true);
        }
      });
    return () => controller.abort();
  }, []);

  const selectTab = (type: ParticipantType) => {
    setTab(type);
    window.history.replaceState(null, '', `#${type}`);
  };

  return (
    <main>
      <h1>{config ? config.conferenceName : 'Conference'} registration</h1>
      {loadError && (
        <p role="alert" className="form-error">
          The registration form is currently unavailable. Please try again later.
        </p>
      )}
      {!config && !loadError && <p>Loading…</p>}
      {config && (
        <>
          <div role="tablist" aria-label="Registration type" className="tabs">
            {TABS.map(({ type, label }) => (
              <button
                key={type}
                role="tab"
                type="button"
                aria-selected={tab === type}
                onClick={() => selectTab(type)}
              >
                {label}
              </button>
            ))}
          </div>
          <div role="tabpanel">
            <RegistrationForm key={tab} type={tab} config={config} />
          </div>
        </>
      )}
    </main>
  );
}
