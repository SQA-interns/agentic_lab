import { useEffect, useState } from "react";
import { loadRegistrationForm } from "../api";
import { messages } from "../messages";
import type { RegistrationFormData } from "../types";
import { Confirmation } from "./Confirmation";
import { RegistrationForm } from "./RegistrationForm";

interface Accepted {
  firstName: string;
  lastName: string;
  registrationId: string;
}

/** Loads the form data, then shows the form until a registration is accepted. */
export function RegistrationPage() {
  const [form, setForm] = useState<RegistrationFormData | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [accepted, setAccepted] = useState<Accepted | null>(null);

  useEffect(() => {
    let active = true;
    loadRegistrationForm()
      .then((data) => {
        if (active) {
          setForm(data);
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

  return (
    <main className="page">
      <h1>{form?.conferenceName ?? messages.title}</h1>
      {accepted !== null ? (
        <Confirmation {...accepted} />
      ) : form !== null ? (
        <RegistrationForm
          form={form}
          onRegistered={(created, firstName, lastName) =>
            setAccepted({ firstName, lastName, registrationId: created.registrationId })
          }
        />
      ) : loadFailed ? (
        <p role="alert" className="error">
          {messages.loadFailed}
        </p>
      ) : (
        <p>{messages.loading}</p>
      )}
    </main>
  );
}
