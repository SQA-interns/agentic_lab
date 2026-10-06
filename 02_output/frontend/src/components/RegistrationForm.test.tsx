import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RegistrationSetup } from "../api/types";
import { RegistrationForm } from "./RegistrationForm";

const SETUP: RegistrationSetup = {
  conferenceName: "Konf",
  consent: { id: "c", text: "I agree" },
  recaptcha: { testMode: true, siteKey: "" },
  options: [{ id: "a", name: "A", category: "MEAL", offeredTo: ["EXTERNAL", "STUDENT"] }],
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
    render(<RegistrationForm setup={SETUP} onAccepted={() => {}} />);
    fill();
    fireEvent.click(screen.getByTestId("submit"));

    expect(await screen.findByTestId("form-error")).toBeInTheDocument();
  });

  it("asks for a new captcha when the server rejected the token", async () => {
    serverSays({ errors: [{ field: "recaptchaToken", code: "RECAPTCHA_FAILED" }] });
    render(<RegistrationForm setup={SETUP} onAccepted={() => {}} />);
    fill();
    fireEvent.click(screen.getByTestId("submit"));

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

  it("reports acceptance with the registration id", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response('{"registrationId":"r1","receivedAt":"t"}', { status: 201 })),
    );
    const accepted = vi.fn();
    render(<RegistrationForm setup={SETUP} onAccepted={accepted} />);
    fill();
    fireEvent.click(screen.getByTestId("submit"));

    await vi.waitFor(() => expect(accepted).toHaveBeenCalledWith("r1"));
  });
});
