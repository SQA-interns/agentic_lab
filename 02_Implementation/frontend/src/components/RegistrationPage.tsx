import { useCallback, useEffect, useState } from "react";
import { fetchConference, type RegistrationSuccess } from "../api/client";
import type { ConferenceConfig } from "../types";
import { Confirmation } from "./Confirmation";
import { RegistrationForm } from "./RegistrationForm";

type PageState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; config: ConferenceConfig }
  | { status: "registered"; result: RegistrationSuccess };

export function RegistrationPage() {
  const [state, setState] = useState<PageState>({ status: "loading" });

  const load = useCallback(() => {
    setState({ status: "loading" });
    fetchConference()
      .then((config) => setState({ status: "ready", config }))
      .catch(() => setState({ status: "error" }));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  switch (state.status) {
    case "loading":
      return <p role="status">Loading registration form…</p>;
    case "error":
      return (
        <div role="alert">
          <p>The registration form could not be loaded. Please try again.</p>
          <button type="button" onClick={load}>
            Retry
          </button>
        </div>
      );
    case "registered":
      return (
        <Confirmation
          type={state.result.type}
          registrationId={state.result.registrationId}
        />
      );
    case "ready":
      return (
        <RegistrationForm
          config={state.config}
          onRegistered={(result) => setState({ status: "registered", result })}
        />
      );
  }
}
