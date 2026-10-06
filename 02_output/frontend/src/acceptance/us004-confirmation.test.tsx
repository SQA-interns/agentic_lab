import { screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  accepted,
  confirmConsentAndRobot,
  fakeBackend,
  fillExternal,
  renderApp,
  submit,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-004 Registration confirmation (UI)", () => {
  it("AC-004-01 an accepted registration shows the confirmation in place of the form", async () => {
    fakeBackend({ status: 201, body: accepted() });
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    const status = await screen.findByRole("status");
    expect(
      within(status).getByRole("heading", { name: "Registration received" }),
    ).toBeInTheDocument();
    expect(status).toHaveTextContent(
      "Thank you, Janez. A confirmation email is on its way to janez.kovacic@example.com.",
    );
    expect(screen.queryByRole("button", { name: "Register" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("First name")).not.toBeInTheDocument();
  });

  it("AC-004-02 a rejected registration shows the backend field errors and keeps the values", async () => {
    fakeBackend({
      status: 400,
      body: {
        code: "VALIDATION_FAILED",
        message: "Some fields are not valid.",
        fieldErrors: [
          { field: "email", code: "INVALID_FORMAT", message: "Enter a valid email address." },
        ],
      },
    });
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    const email = screen.getByLabelText("Email");
    await waitFor(() => expect(email).toHaveAccessibleDescription("Enter a valid email address."));
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email).toHaveValue("janez.kovacic@example.com");
    expect(screen.getByLabelText("First name")).toHaveValue("Janez");
    expect(screen.getByLabelText("Organization / institution")).toHaveValue(
      "Inštitut Jožef Stefan",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("AC-004-03 a storage failure shows a general error and no confirmation", async () => {
    fakeBackend({
      status: 503,
      body: {
        code: "STORAGE_FAILED",
        message: "Your registration could not be saved. Please try again later.",
      },
    });
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your registration could not be saved. Please try again later.",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Register" })).toBeEnabled();
  });

  it("AC-004-03 an unreachable backend shows a general error and no confirmation", async () => {
    fakeBackend("network-error");
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    const alert = await screen.findByRole("alert");
    expect(alert.textContent?.trim()).not.toBe("");
    expect(alert).not.toHaveTextContent("TypeError");
    expect(alert).not.toHaveTextContent("Failed to fetch");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
