import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MESSAGES,
  check,
  fillStudent,
  input,
  postedBody,
  renderForm,
  stubApi,
  submit,
  type,
  waitForPost,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("US-002 student form", () => {
  it("AC-002-01 submits a complete student registration", async () => {
    const api = stubApi();
    await renderForm();
    fillStudent();
    check("option-ws-ai");
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body).toEqual({
      type: "STUDENT",
      firstName: "Luka",
      lastName: "Kranjc",
      email: "luka@example.si",
      studyInstitution: "Univerza v Mariboru",
      studyProgramme: "Informatika",
      studentId: "93120001",
      optionIds: ["ws-ai"],
      consentGiven: true,
      recaptchaToken: "test-pass",
    });
  });

  it("AC-002-02 identifies each empty student field and does not submit", async () => {
    const api = stubApi();
    await renderForm();
    check("type-STUDENT");
    check("consent");
    check("recaptcha");
    submit();

    for (const field of [
      "firstName",
      "lastName",
      "email",
      "studyInstitution",
      "studyProgramme",
      "studentId",
    ]) {
      expect(await screen.findByTestId(`error-${field}`)).toHaveTextContent(MESSAGES.REQUIRED);
    }
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-002-03 treats a no-break-space student ID as empty", async () => {
    const api = stubApi();
    await renderForm();
    fillStudent({ studentId: "  ", studyProgramme: "   " });
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-studentId")).toHaveTextContent(MESSAGES.REQUIRED);
    expect(screen.getByTestId("error-studyProgramme")).toHaveTextContent(MESSAGES.REQUIRED);
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-002-04 identifies an invalid student email", async () => {
    const api = stubApi();
    await renderForm();
    fillStudent({ email: "luka.kranjc@student" });
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-email")).toHaveTextContent(MESSAGES.INVALID_EMAIL);
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-002-05 AC-002-06 does not show options not offered to students", async () => {
    stubApi();
    await renderForm();
    expect(screen.getByTestId("option-ev-gala")).toBeInTheDocument();

    check("type-STUDENT");

    expect(screen.queryByTestId("option-ev-gala")).not.toBeInTheDocument();
    expect(screen.getByTestId("option-ws-ai")).toBeInTheDocument();
    expect(screen.getByTestId("option-meal-lunch")).toBeInTheDocument();
  });

  it("AC-002-05 does not send an option hidden by switching to student", async () => {
    const api = stubApi();
    await renderForm();
    check("option-ev-gala");
    fillStudent();
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body.optionIds).not.toContain("ev-gala");
  });

  it("AC-002-08 requires consent from students, not preselected", async () => {
    const api = stubApi();
    await renderForm();
    fillStudent();
    expect(input("consent").checked).toBe(false);
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-consentGiven")).toHaveTextContent(
      MESSAGES.CONSENT_REQUIRED,
    );
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-002-09 shows student fields instead of the organization field", async () => {
    stubApi();
    await renderForm();
    expect(screen.getByTestId("organization")).toBeInTheDocument();
    expect(screen.queryByTestId("studentId")).not.toBeInTheDocument();

    check("type-STUDENT");

    expect(screen.queryByTestId("organization")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Study institution")).toBeInTheDocument();
    expect(screen.getByLabelText("Study programme")).toBeInTheDocument();
    expect(screen.getByLabelText("Student ID")).toBeInTheDocument();
  });

  it("AC-002-09 does not send the organization for a student", async () => {
    const api = stubApi();
    await renderForm();
    type("organization", "Podjetje d.o.o.");
    check("type-STUDENT");
    fillStudent();
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body).not.toHaveProperty("organization");
  });
});
