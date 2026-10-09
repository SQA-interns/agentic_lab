import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { App } from "../App";
import { created, registrationPosts, stubApi } from "./support/api-stub";
import { check, chooseType, fill, fillValidStudent, submit } from "./support/form";

afterEach(() => {
  cleanup();
});

describe("US-002 Student registration (UI)", () => {
  it("AC-002-01 submits exactly the entered student registration", async () => {
    const calls = stubApi(created());
    render(<App />);
    await chooseType("STUDENT");
    fillValidStudent();
    check("option-other-career-fair");

    submit();

    await screen.findByTestId("confirmation");
    const posts = registrationPosts(calls);
    expect(posts).toHaveLength(1);
    expect(posts[0]!.body).toEqual({
      type: "STUDENT",
      firstName: "Luka",
      lastName: "Kranjc",
      email: "luka.kranjc@example.si",
      studyInstitution: "Fakulteta za računalništvo in informatiko",
      studyProgramme: "Računalništvo in informatika",
      studentId: "63200001",
      optionIds: ["other-career-fair"],
      consentIds: ["data-processing"],
      captchaToken: "test-valid",
    });
  });

  it("AC-002-02 the student form asks for the student fields and not for organization", async () => {
    stubApi();
    render(<App />);

    await chooseType("STUDENT");

    expect(screen.getByLabelText("First name")).toBe(screen.getByTestId("field-firstName"));
    expect(screen.getByLabelText("Last name")).toBe(screen.getByTestId("field-lastName"));
    expect(screen.getByLabelText("Email")).toBe(screen.getByTestId("field-email"));
    expect(screen.getByLabelText("Study institution")).toBe(
      screen.getByTestId("field-studyInstitution"),
    );
    expect(screen.getByLabelText("Study programme")).toBe(
      screen.getByTestId("field-studyProgramme"),
    );
    expect(screen.getByLabelText("Student ID")).toBe(screen.getByTestId("field-studentId"));
    expect(screen.queryByTestId("field-organization")).toBeNull();
  });

  it("AC-002-11 no consent is preselected on the student form", async () => {
    stubApi();
    render(<App />);

    await chooseType("STUDENT");

    expect((screen.getByTestId("consent-data-processing") as HTMLInputElement).checked).toBe(false);
  });

  it("AC-002-15 an empty required student field is shown next to it and nothing is sent", async () => {
    const calls = stubApi();
    render(<App />);
    await chooseType("STUDENT");
    fillValidStudent();
    fill("studentId", "");

    submit();

    const error = await screen.findByTestId("error-studentId");
    expect(error.textContent).not.toBe("");
    expect(screen.getByTestId("field-studentId").getAttribute("aria-describedby")).toContain(
      error.id,
    );
    expect(registrationPosts(calls)).toHaveLength(0);
  });
});
