// US-002 Student registration: the student form as the student sees it.
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../../src/App";
import {
  accepted,
  check,
  chooseType,
  confirmConsentAndCaptcha,
  expectFieldError,
  field,
  fillStudent,
  formLoaded,
  mockApi,
  submit,
  type,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-002 student registration", () => {
  it("AC-002-01 shows exactly the student fields after choosing Student", async () => {
    mockApi(accepted({ type: "student" }));
    render(<App />);
    await formLoaded();

    chooseType("Student");

    for (const label of [
      "First name",
      "Last name",
      "Email",
      "Study institution",
      "Study programme",
      "Student ID",
    ]) {
      expect(field(label)).toBeVisible();
    }
    expect(screen.queryByLabelText("Organization / institution")).toBeNull();
  });

  it("AC-002-02 sends the student registration with the selected options", async () => {
    const api = mockApi(accepted({ type: "student" }));
    render(<App />);
    await formLoaded();

    chooseType("Student");
    fillStudent();
    check("City tour");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() => expect(api.posts()).toHaveLength(1));
    const body = api.posts()[0].body as Record<string, unknown>;
    expect(body).toMatchObject({
      type: "student",
      firstName: "Luka",
      lastName: "Kranjc",
      email: "luka.kranjc@student.example.com",
      studyInstitution: "Univerza v Mariboru",
      studyProgramme: "Informatika",
      studentId: "93120045",
      optionIds: ["other-tour"],
      consents: ["data-processing"],
      captchaToken: "test-pass",
    });
    expect(body).not.toHaveProperty("organization");
  });

  it("AC-002-03 shows a required-field error next to an empty student field and sends nothing", async () => {
    const api = mockApi(accepted({ type: "student" }));
    render(<App />);
    await formLoaded();

    chooseType("Student");
    fillStudent();
    type("Student ID", " ");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("Student ID"), "This field is required."),
    );
    expect(api.posts()).toHaveLength(0);
  });

  it("AC-002-04 shows an email format error next to the email field", async () => {
    const api = mockApi(accepted({ type: "student" }));
    render(<App />);
    await formLoaded();

    chooseType("Student");
    fillStudent();
    type("Email", "luka kranjc@student.si");
    confirmConsentAndCaptcha();
    submit();

    await waitFor(() =>
      expectFieldError(field("Email"), "Enter a valid email address."),
    );
    expect(api.posts()).toHaveLength(0);
  });
});
