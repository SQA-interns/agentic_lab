import "@testing-library/jest-dom/vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { formConfig, jsonResponse, mockFetch } from "./test/fixtures";

function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(new RegExp(`^${label}`)), {
    target: { value },
  });
}

function fillExternal() {
  fill("First name", "  Ana  ");
  fill("Last name", "Novak");
  fill("Email", "ana@example.si");
  fill("Organization / institution", "Institut Jožef Stefan");
}

function acceptConsentAndCaptcha() {
  fireEvent.click(screen.getByLabelText(/I agree that my personal data/));
  fireEvent.click(screen.getByLabelText(/I am not a robot/));
}

const success = {
  registrationId: "0d7d1c6e-7c1f-4d1e-9d2e-3f1b8a2c9e11",
  registrationType: "EXTERNAL",
  createdAt: "2026-01-01T10:00:00Z",
};

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows the external form with its fixed fields by default", async () => {
    // AC-001-02
    mockFetch(() => jsonResponse(500, {}));
    render(<App />);

    expect(
      await screen.findByRole("heading", {
        name: "External participant registration",
      }),
    ).toBeInTheDocument();
    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Organization / institution",
    ]) {
      expect(
        screen.getByLabelText(new RegExp(`^${label}`)),
      ).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/^Student ID/)).not.toBeInTheDocument();
  });

  it("switches to the student form with the student fields", async () => {
    // AC-002-02
    mockFetch(() => jsonResponse(500, {}));
    render(<App />);
    fireEvent.click(await screen.findByLabelText("Student"));

    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Study institution",
      "Study programme",
      "Student ID",
    ]) {
      expect(
        screen.getByLabelText(new RegExp(`^${label}`)),
      ).toBeInTheDocument();
    }
    expect(
      screen.queryByLabelText(/^Organization \/ institution/),
    ).not.toBeInTheDocument();
  });

  it("renders active options grouped by category and consent unchecked", async () => {
    // AC-003-01, AC-003-02, AC-002-10
    mockFetch(() => jsonResponse(500, {}));
    render(<App />);

    const workshops = await screen.findByRole("group", { name: "Workshops" });
    expect(within(workshops).getByLabelText("Workshop: AI")).not.toBeChecked();
    expect(
      within(screen.getByRole("group", { name: "Events" })).getByLabelText(
        "Conference dinner",
      ),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole("group", { name: "Meals" })).getByLabelText(
        "Lunch – day 1",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "Other activities" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByLabelText(/I agree that my personal data/),
    ).not.toBeChecked();
  });

  it("submits trimmed data and shows confirmation only after success", async () => {
    // AC-001-01, AC-004-01, AC-001-06
    const calls = mockFetch(() => jsonResponse(201, success));
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fillExternal();
    fireEvent.click(screen.getByLabelText("Workshop: AI"));
    acceptConsentAndCaptcha();

    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("Registration received"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("registration-id")).toHaveTextContent(
      success.registrationId,
    );
    const submission = calls.find(
      (c) => c.url === "/api/registrations/external",
    );
    expect(submission?.init?.method).toBe("POST");
    expect(JSON.parse(String(submission?.init?.body))).toEqual({
      firstName: "Ana",
      lastName: "Novak",
      email: "ana@example.si",
      organization: "Institut Jožef Stefan",
      optionIds: ["ws-ai"],
      consents: { privacy: true },
      captchaToken: "test-mode-pass",
    });
  });

  it("posts student registrations to the student endpoint", async () => {
    // AC-002-01
    const calls = mockFetch(() =>
      jsonResponse(201, { ...success, registrationType: "STUDENT" }),
    );
    render(<App />);
    fireEvent.click(await screen.findByLabelText("Student"));
    fill("First name", "Žiga");
    fill("Last name", "Čeh");
    fill("Email", "ziga@student.uni-lj.si");
    fill("Study institution", "Univerza v Ljubljani");
    fill("Study programme", "Računalništvo");
    fill("Student ID", "63210001");
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText(/student registration has been received/),
    ).toBeInTheDocument();
    const body = JSON.parse(
      String(
        calls.find((c) => c.url === "/api/registrations/student")?.init?.body,
      ),
    );
    expect(body).toMatchObject({
      firstName: "Žiga",
      studyInstitution: "Univerza v Ljubljani",
      studyProgramme: "Računalništvo",
      studentId: "63210001",
    });
    expect(body.organization).toBeUndefined();
  });

  it("does not submit an invalid form and shows field errors", async () => {
    // AC-001-03, AC-001-05, AC-001-08, AC-004-02
    const calls = mockFetch(() => jsonResponse(201, success));
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fill("Email", "not-an-email");
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("Please correct the highlighted fields."),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("This field is required.").length,
    ).toBeGreaterThanOrEqual(3);
    expect(
      screen.getByText("Email must be a valid email address."),
    ).toBeInTheDocument();
    expect(screen.getByText("This consent is required.")).toBeInTheDocument();
    expect(
      screen.getByText("Please confirm that you are not a robot."),
    ).toBeInTheDocument();
    expect(calls.some((c) => c.url.startsWith("/api/registrations"))).toBe(
      false,
    );
    expect(screen.getByLabelText(/^Email/)).toHaveValue("not-an-email");
  });

  it("shows server-side field errors, keeps values and shows no confirmation", async () => {
    // AC-004-02, AC-002-09
    mockFetch(() =>
      jsonResponse(400, {
        error: "VALIDATION_FAILED",
        message: "The registration contains invalid data.",
        fieldErrors: [
          {
            field: "optionIds",
            code: "INACTIVE_OPTION",
            message: "A selected option is no longer available.",
          },
        ],
      }),
    );
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("A selected option is no longer available."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Last name/)).toHaveValue("Novak");
    // captcha is reset after a rejected submission
    expect(screen.getByLabelText(/I am not a robot/)).not.toBeChecked();
  });

  it("shows a captcha error when the backend rejects the token", async () => {
    mockFetch(() =>
      jsonResponse(400, { error: "CAPTCHA_FAILED", message: "failed" }),
    );
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText("The anti-automation check failed. Try again."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
  });

  it.each([
    ["server error", () => jsonResponse(500, { error: "INTERNAL_ERROR" })],
    ["rate limit", () => jsonResponse(429, { error: "RATE_LIMITED" })],
    [
      "network error",
      () => {
        throw new TypeError("network");
      },
    ],
  ])("shows no confirmation on %s", async (_name, respond: () => Response) => {
    // AC-004-03
    mockFetch(() => respond());
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(
      await screen.findByText(
        "Your registration was not completed. Please try again later.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("Registration received")).not.toBeInTheDocument();
  });

  it("starts a new empty registration after confirmation", async () => {
    mockFetch(() => jsonResponse(201, success));
    render(<App />);
    await screen.findByLabelText(/^First name/);
    fillExternal();
    acceptConsentAndCaptcha();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));
    fireEvent.click(
      await screen.findByRole("button", {
        name: "Register another participant",
      }),
    );

    await waitFor(() =>
      expect(screen.getByLabelText(/^First name/)).toHaveValue(""),
    );
    expect(
      screen.getByLabelText(/I agree that my personal data/),
    ).not.toBeChecked();
  });

  it("shows an error when the form configuration cannot be loaded", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(503, {})),
    );
    render(<App />);

    expect(
      await screen.findByText(/registration form is currently unavailable/),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Register" }),
    ).not.toBeInTheDocument();
  });

  it("renders the reCAPTCHA container in production mode", async () => {
    mockFetch(() => jsonResponse(500, {}), {
      ...formConfig,
      captcha: { mode: "RECAPTCHA", siteKey: "site-key" },
    });
    render(<App />);

    expect(await screen.findByTestId("recaptcha")).toBeInTheDocument();
    expect(
      screen.queryByLabelText(/I am not a robot \(test mode\)/),
    ).not.toBeInTheDocument();
    expect(
      document.querySelector('script[src*="www.google.com/recaptcha/api.js"]'),
    ).not.toBeNull();
  });

  it("renders user supplied text as text, not markup", async () => {
    // spec §7.2 output encoding
    mockFetch(() => jsonResponse(500, {}), {
      ...formConfig,
      options: [
        { id: "x", name: "<img src=x onerror=alert(1)>", category: "OTHER" },
      ],
    });
    render(<App />);

    expect(
      await screen.findByText("<img src=x onerror=alert(1)>"),
    ).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });
});
