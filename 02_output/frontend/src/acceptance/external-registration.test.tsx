import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { App } from "../App";
import { registrationPosts, stubApi } from "./support/api-stub";
import { check, chooseType, fill, fillValidExternal, submit } from "./support/form";

afterEach(() => {
  cleanup();
});

describe("US-001 External participant registration (UI)", () => {
  it("AC-001-01 submits exactly the entered external registration", async () => {
    const calls = stubApi();
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();
    check("option-ws-ai");
    check("option-ev-reception");

    submit();

    await screen.findByTestId("confirmation");
    const posts = registrationPosts(calls);
    expect(posts).toHaveLength(1);
    const body = posts[0]!.body as Record<string, unknown>;
    expect(body).toMatchObject({
      type: "EXTERNAL",
      firstName: "Ana",
      lastName: "Novak",
      email: "ana.novak@example.si",
      organization: "Institut Jožef Stefan",
      consentIds: ["data-processing"],
      captchaToken: "test-valid",
    });
    expect(body["optionIds"]).toEqual(expect.arrayContaining(["ws-ai", "ev-reception"]));
    expect(body["optionIds"]).toHaveLength(2);
    expect(body).not.toHaveProperty("studyInstitution");
    expect(body).not.toHaveProperty("studyProgramme");
    expect(body).not.toHaveProperty("studentId");
  });

  it("AC-001-02 the external form asks for the external fields and no student field", async () => {
    stubApi();
    render(<App />);

    await chooseType("EXTERNAL");

    expect(screen.getByLabelText("First name")).toBe(screen.getByTestId("field-firstName"));
    expect(screen.getByLabelText("Last name")).toBe(screen.getByTestId("field-lastName"));
    expect(screen.getByLabelText("Email")).toBe(screen.getByTestId("field-email"));
    expect(screen.getByLabelText("Organization / institution")).toBe(
      screen.getByTestId("field-organization"),
    );
    expect(screen.queryByTestId("field-studyInstitution")).toBeNull();
    expect(screen.queryByTestId("field-studyProgramme")).toBeNull();
    expect(screen.queryByTestId("field-studentId")).toBeNull();
  });

  it("AC-001-13 no consent is preselected on the external form", async () => {
    stubApi();
    render(<App />);

    await chooseType("EXTERNAL");

    const consent = screen.getByTestId("consent-data-processing") as HTMLInputElement;
    expect(consent.type).toBe("checkbox");
    expect(consent.checked).toBe(false);
    expect(
      screen.getByLabelText(
        "I agree to the processing of my personal data for the registration and organisation of the conference.",
      ),
    ).toBe(consent);
  });

  it("AC-001-17 an empty required field is shown next to the field and nothing is sent", async () => {
    const calls = stubApi();
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();
    fill("firstName", "   ");

    submit();

    const error = await screen.findByTestId("error-firstName");
    expect(error.textContent).not.toBe("");
    expect(screen.getByTestId("field-firstName").getAttribute("aria-describedby")).toContain(
      error.id,
    );
    expect(registrationPosts(calls)).toHaveLength(0);
    expect(screen.queryByTestId("confirmation")).toBeNull();
  });

  it("AC-001-17 an invalid email is shown next to the field and nothing is sent", async () => {
    const calls = stubApi();
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();
    fill("email", "ana@example");

    submit();

    const error = await screen.findByTestId("error-email");
    expect(error.textContent).not.toBe("");
    expect(registrationPosts(calls)).toHaveLength(0);
  });
});
