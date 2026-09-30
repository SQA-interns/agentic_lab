export function Home({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
      <p>Choose the registration form that applies to you.</p>
      <ul className="links">
        <li>
          <a href="/register/external">External participant registration</a>
        </li>
        <li>
          <a href="/register/student">Student registration</a>
        </li>
      </ul>
      <p className="hint">
        Organizers: <a href="/organizer">Organizer export</a>
      </p>
    </main>
  );
}

/** The browser's own HTTP Basic dialog handles credentials; this page never sees them. */
export function Organizer() {
  return (
    <main>
      <h1>Organizer export</h1>
      <p>
        Download all accepted registrations as an Excel workbook. Organizer credentials are
        required.
      </p>
      <p>
        <a className="button" href="/api/organizer/export.xlsx">
          Download Excel export
        </a>
      </p>
      <p>
        <a href="/">Back to the start page</a>
      </p>
    </main>
  );
}

export function NotFound() {
  return (
    <main>
      <h1>Page not found</h1>
      <p>
        <a href="/">Back to the start page</a>
      </p>
    </main>
  );
}
