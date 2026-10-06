import { useEffect, useState } from "react";
import { getFormConfig, type FormConfig, type RegistrationAccepted } from "./api";
import { Confirmation } from "./Confirmation";
import { RegistrationForm } from "./RegistrationForm";
import { texts } from "./texts";

type State =
  | { kind: "loading" }
  | { kind: "failed" }
  | { kind: "form"; config: FormConfig }
  | { kind: "done"; config: FormConfig; registration: RegistrationAccepted };

export default function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    getFormConfig()
      .then((config) => active && setState({ kind: "form", config }))
      .catch(() => active && setState({ kind: "failed" }));
    return () => {
      active = false;
    };
  }, []);

  return (
    <main>
      <h1>{texts.pageHeading}</h1>
      {state.kind !== "loading" && state.kind !== "failed" && (
        <p className="conference">{state.config.conferenceName}</p>
      )}
      {state.kind === "loading" && <p>{texts.loading}</p>}
      {state.kind === "failed" && (
        <p role="alert" className="alert">
          {texts.loadError}
        </p>
      )}
      {state.kind === "form" && (
        <RegistrationForm
          config={state.config}
          onAccepted={(registration) =>
            setState({ kind: "done", config: state.config, registration })
          }
        />
      )}
      {state.kind === "done" && <Confirmation registration={state.registration} />}
    </main>
  );
}
