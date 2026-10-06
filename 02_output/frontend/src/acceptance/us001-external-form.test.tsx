import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  CONSENT_TEXT,
  TEST_MODE_CHECKBOX,
  check,
  confirmConsentAndRobot,
  fakeBackend,
  fill,
  fillExternal,
  renderApp,
  submit,
  textFieldLabels,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-001 External participant registration (UI)", () => {
  it("AC-001-01 the external form asks for exactly the external participant fields", async () => {
    fakeBackend();
    await renderApp();

    expect(screen.getByRole("radio", { name: "External participant" })).toBeChecked();
    expect(textFieldLabels()).toEqual([
      "First name",
      "Last name",
      "Email",
      "Organization / institution",
    ]);
    expect(screen.queryByLabelText("Study institution")).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: CONSENT_TEXT })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Workshop: AI in research" })).toBeInTheDocument();
  });

  it("AC-001-11 no consent is preselected", async () => {
    fakeBackend();
    await renderApp();

    expect(screen.getByRole("checkbox", { name: CONSENT_TEXT })).not.toBeChecked();
  });

  it("AC-001-12 submitting without the consent shows an error next to the consent", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    check(TEST_MODE_CHECKBOX);

    submit();

    const consent = screen.getByRole("checkbox", { name: CONSENT_TEXT });
    await waitFor(() => expect(consent).toHaveAccessibleDescription("This consent is required."));
    expect(consent).toHaveAttribute("aria-invalid", "true");
    expect(backend.posted()).toHaveLength(0);
  });

  it("AC-001-13 submitting without the anti-automation check sends nothing", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    check(CONSENT_TEXT);

    submit();

    expect(await screen.findByText("Please confirm that you are not a robot.")).toBeInTheDocument();
    expect(backend.posted()).toHaveLength(0);
  });

  it("AC-001-14 a duplicate email is reported with the backend message", async () => {
    fakeBackend({
      status: 409,
      body: {
        code: "DUPLICATE_EMAIL",
        message: "This email is already registered. Please contact the organizers.",
      },
    });
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This email is already registered. Please contact the organizers.",
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("AC-001-15 empty required fields are reported next to each field before anything is sent", async () => {
    const backend = fakeBackend();
    await renderApp();
    confirmConsentAndRobot();

    submit();

    for (const label of ["First name", "Last name", "Email", "Organization / institution"]) {
      const input = screen.getByLabelText(label);
      await waitFor(() => expect(input).toHaveAccessibleDescription("This field is required."));
      expect(input).toHaveAttribute("aria-invalid", "true");
    }
    expect(backend.posted()).toHaveLength(0);
  });

  it("AC-001-15 whitespace-only values count as empty before anything is sent", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    fill("Last name", "   ");
    confirmConsentAndRobot();

    submit();

    await waitFor(() =>
      expect(screen.getByLabelText("Last name")).toHaveAccessibleDescription(
        "This field is required.",
      ),
    );
    expect(backend.posted()).toHaveLength(0);
  });

  it("AC-001-15 an invalid email is reported next to the email field before anything is sent", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    fill("Email", "janez.example.com");
    confirmConsentAndRobot();

    submit();

    await waitFor(() =>
      expect(screen.getByLabelText("Email")).toHaveAccessibleDescription(
        "Enter a valid email address.",
      ),
    );
    expect(backend.posted()).toHaveLength(0);
  });

  it("AC-001-02 a valid external form is submitted with the entered values", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    check("Workshop: AI in research");
    check("Lunch, day 1");
    confirmConsentAndRobot();

    submit();

    await waitFor(() => expect(backend.posted()).toHaveLength(1));
    expect(backend.posted()[0]).toEqual({
      type: "EXTERNAL",
      firstName: "Janez",
      lastName: "Kovačič",
      email: "janez.kovacic@example.com",
      organization: "Inštitut Jožef Stefan",
      optionIds: ["ws-ai-research", "meal-lunch-day1"],
      consentIds: ["data-processing"],
      antiAutomationToken: "test-mode-pass",
    });
  });
});
