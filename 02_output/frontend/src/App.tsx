import { useState } from "react";
import type { Accepted, RegistrationType } from "./api";
import { Confirmation } from "./Confirmation";
import { typeLabels } from "./messages";
import { RegistrationForm } from "./RegistrationForm";

/** Type choice → form → confirmation, on one page (specification section 8). */
export function App() {
  const [type, setType] = useState<RegistrationType | null>(null);
  const [accepted, setAccepted] = useState<Accepted | null>(null);

  return (
    <main>
      <h1>Conference registration</h1>
      {accepted ? (
        <Confirmation registration={accepted} />
      ) : (
        <>
          <div
            className="type-choice"
            data-testid="type-choice"
            role="group"
            aria-label="Type"
          >
            {(["EXTERNAL", "STUDENT"] as const).map((t) => (
              <button
                key={t}
                type="button"
                data-testid={`type-${t.toLowerCase()}`}
                aria-pressed={type === t}
                onClick={() => setType(t)}
              >
                {typeLabels[t]}
              </button>
            ))}
          </div>
          {type && (
            <RegistrationForm key={type} type={type} onAccepted={setAccepted} />
          )}
        </>
      )}
    </main>
  );
}
