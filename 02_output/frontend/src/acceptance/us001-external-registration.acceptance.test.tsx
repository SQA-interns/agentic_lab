// US-001 External participant registration, through the rendered form (registration-form.ui.json).
import "@testing-library/jest-dom/vitest";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  CONSENT_TEXT,
  LABELS,
  MESSAGES,
  checkbox,
  chooseType,
  confirmationShown,
  enter,
  fillExternalFields,
  giveConsentAndPassCaptcha,
  installBackend,
  openRegistrationPage,
  submit,
  textField,
  tick,
  validationProblem,
} from "./harness";

const EXTERNAL_LABELS = [LABELS.firstName, LABELS.lastName, LABELS.email, LABELS.organization];

describe("US-001 external participant registration", () => {
  it("AC-001-01 sends a valid external registration and it is accepted", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    tick("Delavnica: testiranje programske opreme");
    tick("Kosilo, prvi dan");
    giveConsentAndPassCaptcha();

    submit();

    await waitFor(() => expect(backend.registrations()).toHaveLength(1));
    const body = backend.registrations()[0]?.body;
    expect(body).toEqual({
      type: "EXTERNAL",
      firstName: "Živa",
      lastName: "Čučnik Šušteršič",
      email: "ziva.cucnik@example.org",
      organization: "Inštitut za računalništvo",
      optionIds: expect.arrayContaining(["ws-testing", "meal-lunch-day1"]),
      consent: true,
      captchaToken: "test-pass",
    });
    expect(body?.optionIds).toHaveLength(2);
    expect(await screen.findByText(MESSAGES.confirmationHeading)).toBeVisible();
  });

  it("AC-001-02 asks for exactly the four external participant fields", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("EXTERNAL");

    for (const label of EXTERNAL_LABELS) {
      expect(textField(label)).toBeVisible();
    }
    expect(screen.getAllByRole("textbox")).toHaveLength(4);
    expect(screen.queryByRole("textbox", { name: LABELS.studentId })).toBeNull();
    expect(checkbox(CONSENT_TEXT)).toBeVisible();
    expect(checkbox("Delavnica: testiranje programske opreme")).toBeVisible();
  });

  it("AC-001-03 reports every empty required field and sends nothing", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    enter(LABELS.lastName, "   ");
    giveConsentAndPassCaptcha();

    submit();

    for (const label of EXTERNAL_LABELS) {
      await waitFor(() => expect(textField(label)).toBeInvalid());
      expect(textField(label)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.required),
      );
    }
    expect(backend.registrations()).toHaveLength(0);
    expect(confirmationShown()).toBe(false);
  });

  it("AC-001-03 reports only the field that is empty", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    enter(LABELS.organization, "");
    giveConsentAndPassCaptcha();

    submit();

    await waitFor(() => expect(textField(LABELS.organization)).toBeInvalid());
    expect(textField(LABELS.organization)).toHaveAccessibleDescription(
      expect.stringContaining(MESSAGES.required),
    );
    expect(textField(LABELS.firstName)).not.toBeInvalid();
    expect(textField(LABELS.email)).not.toBeInvalid();
    expect(backend.registrations()).toHaveLength(0);
  });

  it("AC-001-05 reports an invalid email before anything is sent", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    enter(LABELS.email, "ziva.cucnik.example.org");
    giveConsentAndPassCaptcha();

    submit();

    await waitFor(() => expect(textField(LABELS.email)).toBeInvalid());
    expect(textField(LABELS.email)).toHaveAccessibleDescription(
      expect.stringContaining(MESSAGES.invalidEmail),
    );
    expect(backend.registrations()).toHaveLength(0);
    expect(confirmationShown()).toBe(false);
  });

  it("AC-001-07 AC-001-08 shows that a selected option is not available", async () => {
    const backend = installBackend({
      onRegistration: () =>
        validationProblem([
          {
            field: "optionIds",
            code: "option_not_selectable",
            message: MESSAGES.optionNotSelectable,
          },
        ]),
    });
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    tick("Slavnostna večerja");
    giveConsentAndPassCaptcha();

    submit();

    expect(await screen.findByText(MESSAGES.optionNotSelectable)).toBeVisible();
    expect(backend.registrations()).toHaveLength(1);
    expect(confirmationShown()).toBe(false);
    expect(textField(LABELS.firstName)).toHaveValue("Živa");
    expect(checkbox("Slavnostna večerja")).toBeChecked();
  });

  it("AC-001-09 reports the missing consent and shows no confirmation", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    tick(LABELS.captcha);

    submit();

    await waitFor(() =>
      expect(checkbox(CONSENT_TEXT)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.consentRequired),
      ),
    );
    expect(confirmationShown()).toBe(false);
    expect(checkbox(CONSENT_TEXT)).not.toBeChecked();
  });

  it("AC-001-10 sends a registration without options", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    giveConsentAndPassCaptcha();

    submit();

    expect(await screen.findByText(MESSAGES.confirmationHeading)).toBeVisible();
    expect(backend.registrations()[0]?.body?.optionIds).toEqual([]);
  });

  it("AC-001-12 reports the missing anti-automation check and shows no confirmation", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    fillExternalFields();
    tick(CONSENT_TEXT);

    submit();

    await waitFor(() =>
      expect(checkbox(LABELS.captcha)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.captchaFailed),
      ),
    );
    expect(confirmationShown()).toBe(false);
  });

  it("AC-001-14 does not preselect the consent, the anti-automation check or any option", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("EXTERNAL");

    expect(checkbox(CONSENT_TEXT)).not.toBeChecked();
    expect(checkbox(LABELS.captcha)).not.toBeChecked();
    for (const box of screen.getAllByRole("checkbox")) {
      expect(box).not.toBeChecked();
    }
  });
});
