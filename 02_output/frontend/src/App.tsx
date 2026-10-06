import { useCallback, useEffect, useState } from "react";
import { fetchSetup } from "./api/client";
import type { RegistrationSetup } from "./api/types";
import { Confirmation } from "./components/Confirmation";
import { RegistrationForm } from "./components/RegistrationForm";
import { MESSAGES } from "./messages";

type State =
  | { kind: "loading" }
  | { kind: "load-error" }
  | { kind: "form"; setup: RegistrationSetup }
  | { kind: "done"; setup: RegistrationSetup; registrationId: string };

export function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  const load = useCallback(() => {
    setState({ kind: "loading" });
    fetchSetup()
      .then((setup) => setState({ kind: "form", setup }))
      .catch(() => setState({ kind: "load-error" }));
  }, []);

  useEffect(load, [load]);

  const title = state.kind === "form" || state.kind === "done" ? state.setup.conferenceName : "";

  return (
    <main>
      <h1>{title ? `${title}: registration` : "Conference registration"}</h1>
      {state.kind === "loading" && <p>Loading…</p>}
      {state.kind === "load-error" && (
        <div data-testid="load-error" role="alert">
          <p>{MESSAGES.LOAD_FAILED}</p>
          <button type="button" onClick={load}>
            Try again
          </button>
        </div>
      )}
      {state.kind === "form" && (
        <RegistrationForm
          setup={state.setup}
          onAccepted={(registrationId) =>
            setState({ kind: "done", setup: state.setup, registrationId })
          }
        />
      )}
      {state.kind === "done" && (
        <Confirmation
          conferenceName={state.setup.conferenceName}
          registrationId={state.registrationId}
        />
      )}
    </main>
  );
}
