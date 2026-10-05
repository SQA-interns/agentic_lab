// US-004 Registration confirmation: shown only after the backend accepted the registration.
import { render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../../src/App";
import {
  accepted,
  confirmConsentAndCaptcha,
  expectFieldError,
  field,
  fillExternal,
  formLoaded,
  mockApi,
  problem,
  submit,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-004 registration confirmation", () => {
  it("AC-004-01 shows the confirmation with the accepted data after acceptance", async () => {
    mockApi(accepted({ firstName: "Žiga", lastName: "Čebašek" }));
    render(<App />);
    await formLoaded();

    fillExternal();
    confirmConsentAndCaptcha();
    submit();

    const confirmation = await screen.findByRole("status");
    expect(
      within(confirmation).getByRole("heading", {
        name: "Registration received",
      }),
    ).toBeVisible();
    expect(confirmation).toHaveTextContent("Žiga Čebašek");
    expect(confirmation).toHaveTextContent("ana.novak@example.com");
    expect(confirmation).toHaveTextContent("Delavnica: testiranje");
    expect(confirmation).toHaveTextContent(
      "3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c",
    );
    expect(screen.queryByRole("button", { name: "Register" })).toBeNull();
  });

  it("AC-004-02 keeps the form with the entered values and the errors when rejected", async () => {
    mockApi(
      problem(400, [
        {
          field: "lastName",
          code: "REQUIRED",
          message: "This field is required.",
        },
      ]),
    );
    render(<App />);
    await formLoaded();

    fillExternal();
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("Last name"), "This field is required."),
    );
    expect(screen.queryByRole("status")).toBeNull();
    expect(screen.queryByText("Registration received")).toBeNull();
    expect(field("First name")).toHaveValue("Ana");
    expect(screen.getByRole("button", { name: "Register" })).toBeVisible();
  });

  it("AC-004-03 shows an error without internal details when storing fails", async () => {
    mockApi(
      problem(
        500,
        [],
        "Internal error",
        "org.postgresql.util.PSQLException: injected commit failure at RegistrationService",
      ),
    );
    render(<App />);
    await formLoaded();

    fillExternal();
    confirmConsentAndCaptcha();
    submit();

    const alerts = await screen.findAllByRole("alert");
    expect(alerts.some((a) => (a.textContent ?? "").trim().length > 0)).toBe(
      true,
    );
    expect(screen.queryByRole("status")).toBeNull();
    expect(document.body).not.toHaveTextContent(
      /PSQLException|org\.postgresql|injected/,
    );
    expect(screen.getByRole("button", { name: "Register" })).toBeVisible();
  });
});
