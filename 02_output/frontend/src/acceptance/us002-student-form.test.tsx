import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  CONSENT_TEXT,
  check,
  chooseType,
  confirmConsentAndRobot,
  fakeBackend,
  fill,
  renderApp,
  submit,
  textFieldLabels,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-002 Student registration (UI)", () => {
  it("AC-002-01 the student form asks for exactly the student fields", async () => {
    fakeBackend();
    await renderApp();

    chooseType("Student");

    expect(textFieldLabels()).toEqual([
      "First name",
      "Last name",
      "Email",
      "Study institution",
      "Study programme",
      "Student ID",
    ]);
    expect(screen.queryByLabelText("Organization / institution")).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: CONSENT_TEXT })).not.toBeChecked();
  });

  it("AC-002-02 a valid student form is submitted with the entered values", async () => {
    const backend = fakeBackend();
    await renderApp();
    chooseType("Student");
    fill("First name", "Špela");
    fill("Last name", "Žagar");
    fill("Email", "spela.zagar@example.com");
    fill("Study institution", "Univerza v Ljubljani");
    fill("Study programme", "Računalništvo in informatika");
    fill("Student ID", "63210001");
    check("Workshop: Open data");
    confirmConsentAndRobot();

    submit();

    await waitFor(() => expect(backend.posted()).toHaveLength(1));
    expect(backend.posted()[0]).toEqual({
      type: "STUDENT",
      firstName: "Špela",
      lastName: "Žagar",
      email: "spela.zagar@example.com",
      studyInstitution: "Univerza v Ljubljani",
      studyProgramme: "Računalništvo in informatika",
      studentId: "63210001",
      optionIds: ["ws-open-data"],
      consentIds: ["data-processing"],
      antiAutomationToken: "test-mode-pass",
    });
  });

  it("AC-002-04 empty student fields are reported next to each field", async () => {
    const backend = fakeBackend();
    await renderApp();
    chooseType("Student");
    confirmConsentAndRobot();

    submit();

    for (const label of ["Study institution", "Study programme", "Student ID"]) {
      await waitFor(() =>
        expect(screen.getByLabelText(label)).toHaveAccessibleDescription("This field is required."),
      );
    }
    expect(backend.posted()).toHaveLength(0);
  });
});
