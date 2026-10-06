import { screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MESSAGES,
  SETUP,
  check,
  fillExternal,
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

describe("US-001 external participant form", () => {
  it("AC-001-01 submits a complete external registration with selected options", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal();
    check("option-ws-ai");
    check("option-ev-gala");
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body).toEqual({
      type: "EXTERNAL",
      firstName: "Ana",
      lastName: "Novak",
      email: "ana.novak@example.si",
      organization: "Institut Jožef Stefan",
      optionIds: ["ws-ai", "ev-gala"],
      consentGiven: true,
      recaptchaToken: "test-pass",
    });
  });

  it("AC-001-02 identifies each empty required field and does not submit", async () => {
    const api = stubApi();
    await renderForm();
    check("consent");
    check("recaptcha");
    submit();

    for (const field of ["firstName", "lastName", "email", "organization"]) {
      expect(await screen.findByTestId(`error-${field}`)).toHaveTextContent(MESSAGES.REQUIRED);
    }
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-001-03 treats whitespace-only values including no-break spaces as empty", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal({ firstName: "   ", organization: " \t   " });
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-firstName")).toHaveTextContent(MESSAGES.REQUIRED);
    expect(screen.getByTestId("error-organization")).toHaveTextContent(MESSAGES.REQUIRED);
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-001-04 sends values without leading and trailing whitespace", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal({
      firstName: "  Ana\t",
      lastName: "  Novak ",
      email: " ana.novak@example.si ",
      organization: " Institut Jožef Stefan ",
    });
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body.firstName).toBe("Ana");
    expect(body.lastName).toBe("Novak");
    expect(body.email).toBe("ana.novak@example.si");
    expect(body.organization).toBe("Institut Jožef Stefan");
  });

  it("AC-001-05 identifies an invalid email and does not submit", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal({ email: "ana@example" });
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-email")).toHaveTextContent(MESSAGES.INVALID_EMAIL);
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-001-06 sends Slovenian characters unchanged", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal({ firstName: "Čeh Žiga", lastName: "Šuštar", organization: "Občina Škofja Loka" });
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body.firstName).toBe("Čeh Žiga");
    expect(body.lastName).toBe("Šuštar");
    expect(body.organization).toBe("Občina Škofja Loka");
  });

  it("AC-001-07 AC-001-08 shows an option error returned by the server", async () => {
    stubApi({
      status: 400,
      body: {
        title: "Validation failed",
        status: 400,
        code: "VALIDATION_FAILED",
        errors: [{ field: "optionIds[0]", code: "INACTIVE_OPTION" }],
      },
    });
    await renderForm();
    fillExternal();
    check("option-meal-lunch");
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-optionIds")).toHaveTextContent(
      "This option is not available. Reload the page and choose again.",
    );
    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
  });

  it("AC-001-09 identifies a missing consent and does not submit", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal();
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-consentGiven")).toHaveTextContent(
      MESSAGES.CONSENT_REQUIRED,
    );
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-001-10 shows the consent wording, not preselected", async () => {
    stubApi();
    await renderForm();

    expect(screen.getByTestId("consent-text")).toHaveTextContent(SETUP.consent.text);
    expect(input("consent").checked).toBe(false);
    expect(input("consent").type).toBe("checkbox");
  });

  it("AC-001-11 shows the offered options grouped by category with display names", async () => {
    stubApi();
    await renderForm();

    const groups: Record<string, string> = {
      WORKSHOP: "AI workshop",
      EVENT: "Gala dinner",
      MEAL: "Lunch",
      OTHER: "Poster session",
    };
    for (const [category, name] of Object.entries(groups)) {
      const section = screen.getByTestId(`options-${category}`);
      expect(within(section).getByLabelText(name)).toBeInTheDocument();
    }
    expect(screen.getByTestId("options-WORKSHOP")).toHaveTextContent("Workshops");
    expect(screen.getByTestId("options-EVENT")).toHaveTextContent("Events");
    expect(screen.getByTestId("options-MEAL")).toHaveTextContent("Meals");
    expect(screen.getByTestId("options-OTHER")).toHaveTextContent("Other activities");
    for (const o of SETUP.options) {
      expect(input(`option-${o.id}`).checked).toBe(false);
    }
  });

  it("AC-001-12 submits a registration without options", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal();
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body.optionIds).toEqual([]);
  });

  it("AC-001-13 submits several options of one category", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal();
    check("option-ws-ai");
    check("option-meal-lunch");
    check("option-other-poster");
    check("consent");
    check("recaptcha");
    submit();

    const body = await waitForPost(api);
    expect(body.optionIds).toEqual(["ws-ai", "meal-lunch", "other-poster"]);
  });

  it("AC-001-14 requires the anti-automation check before submitting", async () => {
    const api = stubApi();
    await renderForm();
    fillExternal();
    check("consent");
    submit();

    expect(await screen.findByTestId("error-recaptchaToken")).toHaveTextContent(
      MESSAGES.RECAPTCHA_FAILED,
    );
    expect(postedBody(api)).toBeUndefined();
  });

  it("AC-001-15 shows the duplicate email message at the email field", async () => {
    stubApi({
      status: 409,
      body: { title: "Conflict", status: 409, code: "DUPLICATE_EMAIL" },
    });
    await renderForm();
    fillExternal();
    check("consent");
    check("recaptcha");
    submit();

    expect(await screen.findByTestId("error-email")).toHaveTextContent(MESSAGES.DUPLICATE_EMAIL);
    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
    await waitFor(() => expect(input("email").value).toBe("ana.novak@example.si"));
  });

  it("AC-001-02 shows a field error after leaving an empty required field (NFR-03)", async () => {
    stubApi();
    await renderForm();
    type("lastName", "  ");

    expect(await screen.findByTestId("error-lastName")).toHaveTextContent(MESSAGES.REQUIRED);
  });
});
