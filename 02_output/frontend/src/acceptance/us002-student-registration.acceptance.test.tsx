// US-002 Student registration, through the rendered form (registration-form.ui.json).
import "@testing-library/jest-dom/vitest";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  ACTIVE_OPTIONS,
  CONSENT_TEXT,
  LABELS,
  MESSAGES,
  checkbox,
  chooseType,
  confirmationShown,
  enter,
  fillStudentFields,
  giveConsentAndPassCaptcha,
  installBackend,
  offeredOptionNames,
  openRegistrationPage,
  submit,
  textField,
  tick,
} from "./harness";

const STUDENT_LABELS = [
  LABELS.firstName,
  LABELS.lastName,
  LABELS.email,
  LABELS.studyInstitution,
  LABELS.studyProgramme,
  LABELS.studentId,
];

describe("US-002 student registration", () => {
  it("AC-002-01 sends a valid student registration and it is accepted", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    fillStudentFields();
    tick("Otvoritvena slovesnost");
    giveConsentAndPassCaptcha();

    submit();

    await waitFor(() => expect(backend.registrations()).toHaveLength(1));
    expect(backend.registrations()[0]?.body).toEqual({
      type: "STUDENT",
      firstName: "Žan",
      lastName: "Košir",
      email: "zan.kosir@example.org",
      studyInstitution: "Univerza v Ljubljani",
      studyProgramme: "Računalništvo in informatika",
      studentId: "63210001",
      optionIds: ["ev-opening"],
      consent: true,
      captchaToken: "test-pass",
    });
    expect(await screen.findByText(MESSAGES.confirmationHeading)).toBeVisible();
  });

  it("AC-002-02 asks for exactly the six student fields", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("STUDENT");

    for (const label of STUDENT_LABELS) {
      expect(textField(label)).toBeVisible();
    }
    expect(screen.getAllByRole("textbox")).toHaveLength(6);
    expect(screen.queryByRole("textbox", { name: LABELS.organization })).toBeNull();
    expect(checkbox(CONSENT_TEXT)).toBeVisible();
  });

  it("AC-002-03 reports every empty required field and sends nothing", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    enter(LABELS.studentId, "  ");
    giveConsentAndPassCaptcha();

    submit();

    for (const label of STUDENT_LABELS) {
      await waitFor(() => expect(textField(label)).toBeInvalid());
      expect(textField(label)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.required),
      );
    }
    expect(backend.registrations()).toHaveLength(0);
    expect(confirmationShown()).toBe(false);
  });

  it("AC-002-05 reports an invalid email before anything is sent", async () => {
    const backend = installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    fillStudentFields();
    enter(LABELS.email, "zan.kosir@");
    giveConsentAndPassCaptcha();

    submit();

    await waitFor(() => expect(textField(LABELS.email)).toBeInvalid());
    expect(textField(LABELS.email)).toHaveAccessibleDescription(
      expect.stringContaining(MESSAGES.invalidEmail),
    );
    expect(backend.registrations()).toHaveLength(0);
  });

  it("AC-002-08 reports the missing consent and shows no confirmation", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    fillStudentFields();
    tick(LABELS.captcha);

    submit();

    await waitFor(() =>
      expect(checkbox(CONSENT_TEXT)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.consentRequired),
      ),
    );
    expect(confirmationShown()).toBe(false);
  });

  it("AC-002-09 reports the missing anti-automation check and shows no confirmation", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("STUDENT");
    fillStudentFields();
    tick(CONSENT_TEXT);

    submit();

    await waitFor(() =>
      expect(checkbox(LABELS.captcha)).toHaveAccessibleDescription(
        expect.stringContaining(MESSAGES.captchaFailed),
      ),
    );
    expect(confirmationShown()).toBe(false);
  });

  it("AC-002-10 offers students every active option, the same as the external form", async () => {
    installBackend();
    await openRegistrationPage();
    chooseType("EXTERNAL");
    const offeredToExternal = offeredOptionNames();

    chooseType("STUDENT");

    expect(offeredOptionNames()).toEqual(ACTIVE_OPTIONS.map((option) => option.name));
    expect(offeredOptionNames()).toEqual(offeredToExternal);
  });

  it("AC-002-11 does not preselect the consent, the anti-automation check or any option", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("STUDENT");

    expect(checkbox(CONSENT_TEXT)).not.toBeChecked();
    expect(checkbox(LABELS.captcha)).not.toBeChecked();
    for (const box of screen.getAllByRole("checkbox")) {
      expect(box).not.toBeChecked();
    }
  });

  it("AC-002-12 shows the form of the chosen registration type", async () => {
    installBackend();
    await openRegistrationPage();
    expect(screen.getByRole("radio", { name: LABELS.external })).not.toBeChecked();
    expect(screen.getByRole("radio", { name: LABELS.student })).not.toBeChecked();
    expect(screen.queryAllByRole("textbox")).toHaveLength(0);

    chooseType("STUDENT");
    expect(textField(LABELS.studentId)).toBeVisible();
    expect(screen.queryByRole("textbox", { name: LABELS.organization })).toBeNull();

    chooseType("EXTERNAL");
    expect(textField(LABELS.organization)).toBeVisible();
    expect(screen.queryByRole("textbox", { name: LABELS.studentId })).toBeNull();
    expect(screen.getAllByRole("textbox")).toHaveLength(4);
  });
});
