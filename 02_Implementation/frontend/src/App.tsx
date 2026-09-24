import { RegistrationForm } from './components/RegistrationForm';
import type { RegistrationType } from './types';

const ROUTES: Record<string, RegistrationType> = {
  '/register/external': 'EXTERNAL',
  '/register/student': 'STUDENT',
};

function Home() {
  return (
    <section className="chooser">
      <h2>Choose your registration</h2>
      <ul>
        <li>
          <a className="choice" href="/register/external">
            <strong>External participant</strong>
            <span>For participants from companies, universities and other organizations.</span>
          </a>
        </li>
        <li>
          <a className="choice" href="/register/student">
            <strong>Student</strong>
            <span>For students attending the conference and student activities.</span>
          </a>
        </li>
      </ul>
    </section>
  );
}

interface AppProps {
  path?: string;
}

export function App({ path = window.location.pathname }: AppProps) {
  const type = ROUTES[path.replace(/\/+$/, '')];
  return (
    <>
      <header className="site-header">
        <h1>
          <a href="/">Conference registration</a>
        </h1>
      </header>
      <main className="container">
        {type === undefined ? <Home /> : <RegistrationForm key={type} type={type} />}
      </main>
    </>
  );
}
