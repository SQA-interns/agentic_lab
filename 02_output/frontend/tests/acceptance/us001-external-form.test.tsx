// US-001 External participant registration, through the rendered UI (ui-form.json).
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup } from "@testing-library/react";
import { fill, fillValidExternal, openForm, chooseType, submit } from "./helpers";
import { accepted, mockApi, problem } from "./support";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("US-001 external participant form", () => {
  it("AC-001-01 a complete external registration is sent and confirmed", async () => {
    const api = mockApi(accepted());
    await openForm();
    await fillValidExternal();
    await submit();

    expect(await screen.findByTestId("confirmation")).toHaveTextContent(
      "Thank you, your registration was received.",
    );
    expect(api.registrationCalls()).toHaveLength(1);
    expect(api.registrationCalls()[0].body).toEqual({
      type: "EXTERNAL",
      firstName: "Ana",
      lastName: "Novak",
      email: "ana.novak@example.si",
      organization: "Institut Primer",
      optionIds: ["ws-testing"],
      consentIds: ["data-processing"],
      recaptchaToken: "test-mode-pass",
    });
  });

  it("AC-001-02 the external form shows exactly its four required fields", async () => {
    mockApi();
    await openForm();
    await chooseType("EXTERNAL");

    for (const [name, label] of [
      ["firstName", "First name"],
      ["lastName", "Last name"],
      ["email", "Email"],
      ["organization", "Organization / institution"],
    ]) {
      const input = screen.getByTestId(`field-${name}`);
      expect(input).toBeRequired();
      expect(screen.getByLabelText(label, { exact: false })).toBe(input);
    }
    for (const name of ["studyInstitution", "studyProgramme", "studentId"]) {
      expect(screen.queryByTestId(`field-${name}`)).toBeNull();
    }
  });

  it("AC-001-03 an empty required field is reported and nothing is sent", async () => {
    const api = mockApi();
    await openForm();
    await fillValidExternal();
    await fill("organization", "");
    await submit();

    expect(await screen.findByTestId("error-organization")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
    expect(screen.queryByTestId("confirmation")).toBeNull();
  });

  it("AC-001-04 a value of only no-break spaces counts as empty", async () => {
    const api = mockApi();
    await openForm();
    await fillValidExternal();
    await fill("lastName", "   ");
    await submit();

    expect(await screen.findByTestId("error-lastName")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
  });

  it("AC-001-05 surrounding whitespace including NBSP is removed before sending", async () => {
    const api = mockApi(accepted());
    await openForm();
    await fillValidExternal();
    await fill("firstName", "  Ana  ");
    await fill("organization", " Institut Primer ");
    await submit();

    await screen.findByTestId("confirmation");
    const body = api.registrationCalls()[0].body as Record<string, unknown>;
    expect(body.firstName).toBe("Ana");
    expect(body.organization).toBe("Institut Primer");
  });

  it("AC-001-06 an invalid email is reported and nothing is sent", async () => {
    const api = mockApi();
    await openForm();
    await fillValidExternal();
    await fill("email", "ana.novak@");
    await submit();

    expect(await screen.findByTestId("error-email")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
  });

  it("AC-001-07 Slovenian characters are sent unchanged", async () => {
    const api = mockApi(accepted());
    await openForm();
    await fillValidExternal();
    await fill("firstName", "Čedomir");
    await fill("lastName", "Šuštaršič");
    await fill("organization", "Žalec d.o.o.");
    await submit();

    await screen.findByTestId("confirmation");
    expect(api.registrationCalls()[0].body).toMatchObject({
      firstName: "Čedomir",
      lastName: "Šuštaršič",
      organization: "Žalec d.o.o.",
    });
  });

  it("AC-001-08 options are offered grouped as workshops, events, meals and other activities", async () => {
    mockApi();
    await openForm();
    await chooseType("EXTERNAL");

    const groups = [
      ["WORKSHOP", "Workshops", ["ws-testing", "ws-security"]],
      ["EVENT", "Events", ["ev-reception"]],
      ["MEAL", "Meals", ["meal-dinner"]],
      ["OTHER", "Other activities", ["other-city-tour"]],
    ] as const;
    for (const [category, heading, ids] of groups) {
      const group = screen.getByTestId(`options-${category}`);
      expect(group).toHaveTextContent(heading);
      for (const id of ids) {
        expect(within(group).getByTestId(`option-${id}`)).toBeInTheDocument();
      }
    }
    expect(screen.getByTestId("options-WORKSHOP")).toHaveTextContent(
      "Delavnica: testiranje programske opreme",
    );
  });

  it("AC-001-12 the mandatory consent is shown with its wording and not preselected", async () => {
    mockApi();
    await openForm();
    await chooseType("EXTERNAL");

    const consent = screen.getByTestId("consent-data-processing");
    expect(consent).not.toBeChecked();
    expect(screen.getByTestId("registration-page")).toHaveTextContent(
      "I agree that my personal data is processed for registering me for the conference.",
    );
  });

  it("AC-001-13 a missing mandatory consent is reported and nothing is sent", async () => {
    const api = mockApi();
    await openForm();
    await fillValidExternal();
    fireEvent.click(screen.getByTestId("consent-data-processing"));
    await submit();

    expect(await screen.findByTestId("error-consentIds")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
  });

  it("AC-001-14 without the anti-automation check nothing is sent", async () => {
    const api = mockApi();
    await openForm();
    await fillValidExternal();
    fireEvent.click(screen.getByTestId("recaptcha-test"));
    await submit();

    expect(await screen.findByTestId("error-recaptchaToken")).toBeVisible();
    expect(api.registrationCalls()).toHaveLength(0);
  });

  it("AC-001-15 an already registered email is reported without confirmation", async () => {
    mockApi(problem(409, "This email address is already registered."));
    await openForm();
    await fillValidExternal();
    await submit();

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "This email address is already registered.",
    );
    await waitFor(() => expect(screen.queryByTestId("confirmation")).toBeNull());
  });
});
