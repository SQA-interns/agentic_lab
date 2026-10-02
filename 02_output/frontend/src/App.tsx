import { useEffect, useState } from "react";
import { type ConferenceOption, type FormConfig, loadFormConfig, loadOptions } from "./api";
import { RegistrationForm } from "./RegistrationForm";
import { CONFIRMATION, FAILURES, PAGE_TITLE } from "./texts";

type Page =
  | { state: "loading" }
  | { state: "loadFailed" }
  | { state: "form"; config: FormConfig; options: ConferenceOption[] }
  | { state: "confirmed"; config: FormConfig };

export function App() {
  const [page, setPage] = useState<Page>({ state: "loading" });

  useEffect(() => {
    let cancelled = false;
    Promise.all([loadFormConfig(), loadOptions()])
      .then(([config, options]) => {
        if (!cancelled) {
          setPage({ state: "form", config, options });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPage({ state: "loadFailed" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>{PAGE_TITLE}</h1>
      {(page.state === "form" || page.state === "confirmed") && (
        <p className="conference">{page.config.conferenceName}</p>
      )}
      {page.state === "loadFailed" && (
        <p role="alert" className="failure">
          {FAILURES.loadFailed}
        </p>
      )}
      {page.state === "form" && (
        <RegistrationForm
          config={page.config}
          options={page.options}
          onAccepted={() => setPage({ state: "confirmed", config: page.config })}
        />
      )}
      {page.state === "confirmed" && (
        // The confirmation replaces the form only after the backend accepted (BR-06).
        <section role="status" className="confirmation">
          <h2>{CONFIRMATION.heading}</h2>
          <p>{CONFIRMATION.text}</p>
        </section>
      )}
    </main>
  );
}
