import { useEffect, useState } from 'react';
import { fetchCatalog, type Accepted, type Catalog, type FormType } from './api';
import { Home, NotFound, Organizer } from './components/Pages';
import { RegistrationForm } from './components/RegistrationForm';
import { Success } from './components/Success';

const FORMS: Record<string, FormType> = {
  '/register/external': 'external',
  '/register/student': 'student',
};

export function App({ path = window.location.pathname }: { path?: string }) {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [accepted, setAccepted] = useState<Accepted | null>(null);
  const needsCatalog = path === '/' || path in FORMS;

  useEffect(() => {
    if (!needsCatalog) return;
    fetchCatalog()
      .then(setCatalog)
      .catch(() => {
        setLoadFailed(true);
      });
  }, [needsCatalog]);

  useEffect(() => {
    document.title = accepted
      ? 'Registration received – Lab Conference'
      : 'Lab Conference registration';
  }, [accepted]);

  if (path === '/organizer') return <Organizer />;
  if (!needsCatalog) return <NotFound />;
  if (accepted) return <Success accepted={accepted} />;
  if (loadFailed) {
    return (
      <main>
        <h1>Registration unavailable</h1>
        <p role="alert">The registration service cannot be reached. Please try again later.</p>
      </main>
    );
  }
  if (!catalog) {
    return (
      <main aria-busy="true">
        <p>Loading…</p>
      </main>
    );
  }
  const form = FORMS[path];
  if (!form) return <Home title={catalog.conferenceTitle} />;
  return <RegistrationForm form={form} catalog={catalog} onAccepted={setAccepted} />;
}
