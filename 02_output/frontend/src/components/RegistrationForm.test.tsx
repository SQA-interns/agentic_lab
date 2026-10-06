import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RegistrationSetup } from "../api/types";
import { RegistrationForm } from "./RegistrationForm";

const SETUP: RegistrationSetup = {
  conferenceName: "Konf",
  consent: { id: "c", text: "I agree" },
  recaptcha: { testMode: true, siteKey: "" },
  options: [
    { id: "a", name: "A", category: "MEAL", offeredTo: ["EXTERNAL", "STUDENT"] },
    { id: "g", name: "G", category: "EVENT", offeredTo: ["EXTERNAL"] },
  ],
};

function fill() {
  for (const [id, v] of Object.entries({
    firstName: "Ana",
    lastName: "N",
    email: "a@b.si",
    organization: "O",
  })) {
    fireEvent.change(screen.getByTestId(id), { target: { value: v } });
  }
  fireEvent.click(screen.getByTestId("consent"));
  fireEvent.click(screen.getByTestId("recaptcha"));
}

/** Renders the form, completes it and submits it. */
function submitCompleted(onAccepted: (id: string) => void = () => {}) {
  render(<RegistrationForm setup={SETUP} onAccepted={onAccepted} />);
  fill();
  fireEvent.click(screen.getByTestId("submit"));
}

function serverSays(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(JSON.stringify(body), { status: 400 })),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("RegistrationForm", () => {
  it("shows a general error for a field error it cannot place", async () => {
    serverSays({ errors: [{ field: "type", code: "REQUIRED" }] });
    submitCompleted();

    expect(await screen.findByTestId("form-error")).toBeInTheDocument();
  });

  it("asks for a new captcha when the server rejected the token", async () => {
    serverSays({ errors: [{ field: "recaptchaToken", code: "RECAPTCHA_FAILED" }] });
    submitCompleted();

    expect(await screen.findByTestId("error-recaptchaToken")).toBeInTheDocument();
    expect((screen.getByTestId("recaptcha") as HTMLInputElement).checked).toBe(false);
  });

  it("unchecks an option on a second click and links field errors for screen readers", async () => {
    render(<RegistrationForm setup={SETUP} onAccepted={() => {}} />);
    const option = screen.getByTestId("option-a") as HTMLInputElement;
    fireEvent.click(option);
    fireEvent.click(option);
    expect(option.checked).toBe(false);

    fireEvent.blur(screen.getByTestId("email"));
    expect(screen.getByTestId("email")).toHaveAttribute("aria-describedby", "error-email");
    expect(screen.getByTestId("email")).toHaveAttribute("aria-invalid", "true");
  });

  it("keeps a selected option offered to both types when switching type", async () => {
    const f = vi.fn(
      async () => new Response('{"registrationId":"r","receivedAt":"t"}', { status: 201 }),
    );
    vi.stubGlobal("fetch", f);
    render(<RegistrationForm setup={SETUP} onAccepted={() => {}} />);
    fireEvent.click(screen.getByTestId("option-a"));
    fireEvent.click(screen.getByTestId("option-g"));
    fireEvent.click(screen.getByTestId("type-STUDENT"));
    for (const [id, v] of Object.entries({
      firstName: "L",
      lastName: "K",
      email: "l@k.si",
      studyInstitution: "U",
      studyProgramme: "P",
      studentId: "1",
    })) {
      fireEvent.change(screen.getByTestId(id), { target: { value: v } });
    }
    fireEvent.click(screen.getByTestId("consent"));
    fireEvent.click(screen.getByTestId("recaptcha"));
    fireEvent.click(screen.getByTestId("submit"));

    await vi.waitFor(() => expect(f).toHaveBeenCalled());
    const init = (f.mock.calls[0] as unknown as [string, RequestInit])[1];
    const body = JSON.parse(String(init.body));
    expect(body.optionIds).toEqual(["a"]);
  });

  it("keeps the captcha token when only other fields were rejected", async () => {
    serverSays({ errors: [{ field: "email", code: "INVALID_EMAIL" }] });
    submitCompleted();

    expect(await screen.findByTestId("error-email")).toBeInTheDocument();
    expect((screen.getByTestId("recaptcha") as HTMLInputElement).checked).toBe(true);
    expect(screen.queryByTestId("form-error")).not.toBeInTheDocument();
  });

  it("reports acceptance with the registration id", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response('{"registrationId":"r1","receivedAt":"t"}', { status: 201 })),
    );
    const accepted = vi.fn();
    submitCompleted(accepted);

    await vi.waitFor(() => expect(accepted).toHaveBeenCalledWith("r1"));
  });
});
