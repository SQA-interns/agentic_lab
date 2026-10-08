// US-002 Student registration, through the rendered UI (ui-form.json).
import { cleanup, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { chooseType, fill, fillValidStudent, openForm, submit } from "./helpers";
import { accepted, mockApi } from "./support";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("US-002 student form", () => {
  it("AC-002-01 a complete student registration is sent and confirmed", async () => {
    const api = mockApi(accepted());
    await openForm();
    await fillValidStudent();
    await submit();

    expect(await screen.findByTestId("confirmation")).toBeVisible();
    expect(api.registrationCalls()[0].body).toEqual({
      type: "STUDENT",
      firstName: "Luka",
      lastName: "Horvat",
      email: "luka.horvat@student.example.si",
      studyInstitution: "Univerza v Mariboru",
      studyProgramme: "Informatika",
      studentId: "E1234567",
      optionIds: ["ev-career-fair"],
      consentIds: ["data-processing"],
      recaptchaToken: "test-mode-pass",
    });
  });

  it("AC-002-02 the student form shows exactly its six required fields", async () => {
    mockApi();
    await openForm();
    await chooseType("STUDENT");

    for (const [name, label] of [
      ["firstName", "First name"],
      ["lastName", "Last name"],
      ["email", "Email"],
      ["studyInstitution", "Study institution"],
      ["studyProgramme", "Study programme"],
      ["studentId", "Student ID"],
    ]) {
      const input = screen.getByTestId(`field-${name}`);
      expect(input).toBeRequired();
      expect(screen.getByLabelText(label, { exact: false })).toBe(input);
    }
    expect(screen.queryByTestId("field-organization")).toBeNull();
  });

  it("AC-002-03 a student field of only no-break spaces is reported and nothing is sent", async () => {
    const api = mockApi();
    await openForm();
    await fillValidStudent();
    await fill("studentId", " ");
    await submit();

    expect(await screen.findByTestId("error-studentId")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
  });

  it("AC-002-06 options not available to students are not offered on the student form", async () => {
    mockApi();
    await openForm();
    await chooseType("STUDENT");

    expect(screen.queryByTestId("option-ws-security")).toBeNull();
    expect(screen.getByTestId("option-ev-career-fair")).toBeInTheDocument();
    expect(screen.getByTestId("option-ws-testing")).toBeInTheDocument();

    await chooseType("EXTERNAL");
    expect(screen.getByTestId("option-ws-security")).toBeInTheDocument();
    expect(screen.queryByTestId("option-ev-career-fair")).toBeNull();
  });
});
