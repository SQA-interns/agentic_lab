// US-004 Registration confirmation, through the rendered form (registration-form.ui.json, views).
import "@testing-library/jest-dom/vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  type Answer,
  LABELS,
  MESSAGES,
  chooseType,
  confirmationShown,
  fillExternalFields,
  fillStudentFields,
  giveConsentAndPassCaptcha,
  installBackend,
  openRegistrationPage,
  submit,
  textField,
  validationProblem,
} from "./harness";

async function submitValidExternal(): Promise<void> {
  await openRegistrationPage();
  chooseType("EXTERNAL");
  fillExternalFields();
  giveConsentAndPassCaptcha();
  submit();
}

describe("US-004 registration confirmation", () => {
  it("AC-004-01 shows the confirmation only after the registration was accepted", async () => {
    let accept: (answer: Answer) => void = () => undefined;
    const backend = installBackend({
      onRegistration: () =>
        new Promise<Answer>((resolve) => {
          accept = resolve;
        }),
    });

    await submitValidExternal();
    await waitFor(() => expect(backend.registrations()).toHaveLength(1));
    expect(confirmationShown()).toBe(false);

    accept({
      status: 201,
      body: { id: "3f0e7a52-8a54-4d0e-9f43-1c6a2b7d9e10", acceptedAt: "2026-10-02T10:15:30Z" },
    });

    const confirmation = await screen.findByRole("status");
    expect(within(confirmation).getByText(MESSAGES.confirmationHeading)).toBeVisible();
    expect(screen.queryByRole("button", { name: LABELS.submit })).toBeNull();
    expect(screen.queryAllByRole("textbox")).toHaveLength(0);
  });

  it("AC-004-01 shows the confirmation for a student registration", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    fillStudentFields();
    giveConsentAndPassCaptcha();

    submit();

    const confirmation = await screen.findByRole("status");
    expect(within(confirmation).getByText(MESSAGES.confirmationHeading)).toBeVisible();
  });

  it("AC-004-02 shows the reasons of a rejection next to the fields and keeps the values", async () => {
    const backend = installBackend({
      onRegistration: () =>
        validationProblem([
          { field: "email", code: "invalid_format", message: MESSAGES.invalidEmail },
          { field: "organization", code: "too_long", message: "Vnos je predolg." },
        ]),
    });

    await submitValidExternal();

    await waitFor(() => expect(textField(LABELS.email)).toBeInvalid());
    expect(backend.registrations()).toHaveLength(1);
    expect(textField(LABELS.email)).toHaveAccessibleDescription(
      expect.stringContaining(MESSAGES.invalidEmail),
    );
    expect(textField(LABELS.organization)).toBeInvalid();
    expect(textField(LABELS.organization)).toHaveAccessibleDescription(
      expect.stringContaining("Vnos je predolg."),
    );
    expect(textField(LABELS.firstName)).not.toBeInvalid();
    expect(confirmationShown()).toBe(false);
    expect(textField(LABELS.firstName)).toHaveValue("Živa");
    expect(textField(LABELS.email)).toHaveValue("ziva.cucnik@example.org");
    expect(textField(LABELS.organization)).toHaveValue("Inštitut za računalništvo");
  });

  it("AC-004-03 tells the participant when the registration could not be stored", async () => {
    installBackend({
      onRegistration: () => ({
        status: 503,
        headers: { "Content-Type": "application/problem+json" },
        body: { type: "about:blank", title: "Storitev ni na voljo", status: 503 },
      }),
    });

    await submitValidExternal();

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText(MESSAGES.notReceived)).toBeVisible();
    expect(confirmationShown()).toBe(false);
    expect(textField(LABELS.firstName)).toHaveValue("Živa");
    expect(screen.getByRole("button", { name: LABELS.submit })).toBeVisible();
  });

  it("AC-004-03 tells the participant when the backend cannot be reached", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    giveConsentAndPassCaptcha();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new TypeError("network error"))),
    );

    submit();

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText(MESSAGES.notReceived)).toBeVisible();
    expect(confirmationShown()).toBe(false);
  });

  it("AC-004-02 tells the participant when too many attempts were made", async () => {
    installBackend({
      onRegistration: () => ({
        status: 429,
        headers: { "Content-Type": "application/problem+json", "Retry-After": "30" },
        body: { type: "about:blank", title: "Preveč zahtev", status: 429 },
      }),
    });

    await submitValidExternal();

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText(MESSAGES.tooManyRequests)).toBeVisible();
    expect(confirmationShown()).toBe(false);
  });
});
