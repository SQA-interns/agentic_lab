// US-001 External participant registration: the form as the participant sees it.
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../../src/App";
import {
  CONSENT_TEXT,
  PHOTO_CONSENT_TEXT,
  accepted,
  check,
  confirmConsentAndCaptcha,
  expectFieldError,
  field,
  fillExternal,
  formLoaded,
  mockApi,
  problem,
  submit,
  type,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-001 external participant registration", () => {
  it("AC-001-01 shows exactly the external participant fields by default", async () => {
    mockApi(accepted());
    render(<App />);
    await formLoaded();

    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Organization / institution",
    ]) {
      expect(field(label)).toBeVisible();
    }
    for (const label of [
      "Study institution",
      "Study programme",
      "Student ID",
    ]) {
      expect(screen.queryByLabelText(label)).toBeNull();
    }
    expect(
      screen.getByRole("radio", { name: "External participant" }),
    ).toBeChecked();
  });

  it("AC-001-02 sends the external registration with the selected options and consents", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    check("Delavnica: testiranje");
    check("Kosilo");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() => expect(api.posts()).toHaveLength(1));
    const body = api.posts()[0].body as Record<string, unknown>;
    expect(api.posts()[0].url).toContain("/api/registrations");
    expect(body).toMatchObject({
      type: "external",
      firstName: "Ana",
      lastName: "Novak",
      email: "ana.novak@example.com",
      organization: "Institut Jozef Stefan",
      consents: ["data-processing"],
      captchaToken: "test-pass",
    });
    expect([...(body.optionIds as string[])].sort()).toEqual([
      "meal-lunch",
      "ws-testing",
    ]);
    expect(body).not.toHaveProperty("studentId");
  });

  it("AC-001-03 shows a required-field error next to an empty field and sends nothing", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    type("Organization / institution", "");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(
        field("Organization / institution"),
        "This field is required.",
      ),
    );
    expect(api.posts()).toHaveLength(0);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("AC-001-04 treats a whitespace-only field as empty", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    type("First name", "   ");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("First name"), "This field is required."),
    );
    expect(api.posts()).toHaveLength(0);
  });

  it("AC-001-06 shows an email format error next to the email field and sends nothing", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    type("Email", "ana.novak@example");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("Email"), "Enter a valid email address."),
    );
    expect(api.posts()).toHaveLength(0);
  });

  it("AC-001-06 shows the backend's email error next to the email field", async () => {
    mockApi(
      problem(400, [
        {
          field: "email",
          code: "INVALID_EMAIL",
          message: "Enter a valid email address.",
        },
      ]),
    );
    render(<App />);
    await formLoaded();

    fillExternal();
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("Email"), "Enter a valid email address."),
    );
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("AC-001-12 shows every consent unchecked", async () => {
    mockApi(accepted());
    render(<App />);
    await formLoaded();

    expect(
      screen.getByRole("checkbox", { name: CONSENT_TEXT }),
    ).not.toBeChecked();
    expect(
      screen.getByRole("checkbox", { name: PHOTO_CONSENT_TEXT }),
    ).not.toBeChecked();
  });

  it("AC-001-13 shows a consent error next to the missing mandatory consent and sends nothing", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    check("I am not a robot (test mode)");
    submit();

    await waitFor(() =>
      expectFieldError(
        screen.getByRole("checkbox", { name: CONSENT_TEXT }),
        "This consent is required.",
      ),
    );
    expect(api.posts()).toHaveLength(0);
  });

  it("AC-001-14 asks to pass the anti-automation check before sending", async () => {
    const api = mockApi(accepted());
    render(<App />);
    await formLoaded();

    fillExternal();
    check(CONSENT_TEXT);
    submit();

    await waitFor(() =>
      expect(
        screen.getByText("Please confirm that you are not a robot."),
      ).toBeVisible(),
    );
    expect(api.posts()).toHaveLength(0);
  });

  it("AC-001-15 shows the duplicate-email message from the backend", async () => {
    mockApi(
      problem(409, [
        {
          field: "email",
          code: "DUPLICATE_EMAIL",
          message:
            "This email is already registered. Please contact the organizers.",
        },
      ]),
    );
    render(<App />);
    await formLoaded();

    fillExternal();
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expect(
        screen.getByText(/already registered\. Please contact the organizers/),
      ).toBeVisible(),
    );
    expect(screen.queryByRole("status")).toBeNull();
  });
});
