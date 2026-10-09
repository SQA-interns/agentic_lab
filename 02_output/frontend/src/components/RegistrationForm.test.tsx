import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { App } from "../App";
import { RegistrationForm } from "./RegistrationForm";
import type { RegistrationFormData } from "../types";

const form: RegistrationFormData = {
  conferenceName: "Konferenca",
  captcha: { mode: "test", siteKey: null },
  categories: [
    {
      category: "workshop",
      maxSelections: 1,
      options: [
        { id: "ws-a", name: "A", availableTo: ["EXTERNAL", "STUDENT"] },
        { id: "ws-b", name: "B", availableTo: ["EXTERNAL", "STUDENT"] },
      ],
    },
    {
      category: "event",
      maxSelections: 1,
      options: [{ id: "ev-ext", name: "Dinner", availableTo: ["EXTERNAL"] }],
    },
  ],
  consents: [{ id: "data", text: "I agree", mandatory: true }],
};

function respond(status: number, body: unknown) {
  const fetchMock = vi.fn(
    async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function fillExternal() {
  fireEvent.click(screen.getByTestId("type-external"));
  fireEvent.change(screen.getByTestId("field-firstName"), { target: { value: " Ana " } });
  fireEvent.change(screen.getByTestId("field-lastName"), { target: { value: "Novak" } });
  fireEvent.change(screen.getByTestId("field-email"), { target: { value: "ana@example.si" } });
  fireEvent.change(screen.getByTestId("field-organization"), { target: { value: "IJS" } });
  fireEvent.click(screen.getByTestId("consent-data"));
  fireEvent.click(screen.getByTestId("captcha-test"));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("registration form", () => {
  it("shows nothing but the type choice until a type is chosen", () => {
    render(<RegistrationForm form={form} onRegistered={() => {}} />);

    expect(screen.queryByTestId("field-firstName")).toBeNull();
    expect(screen.queryByTestId("submit")).toBeNull();
  });

  it("drops selected options that the new type cannot have", () => {
    const fetchMock = respond(201, { registrationId: "id", registeredAt: "t", type: "STUDENT" });
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fireEvent.click(screen.getByTestId("type-external"));
    fireEvent.click(screen.getByTestId("option-ev-ext"));
    fireEvent.click(screen.getByTestId("option-ws-a"));
    fireEvent.click(screen.getByTestId("type-student"));
    fireEvent.click(screen.getByTestId("type-external"));

    expect((screen.getByTestId("option-ev-ext") as HTMLInputElement).checked).toBe(false);
    expect((screen.getByTestId("option-ws-a") as HTMLInputElement).checked).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends trimmed values and reports the accepted registration", async () => {
    const fetchMock = respond(201, { registrationId: "id-1", registeredAt: "t", type: "EXTERNAL" });
    const onRegistered = vi.fn();
    render(<RegistrationForm form={form} onRegistered={onRegistered} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("option-ws-a"));
    fireEvent.click(screen.getByTestId("option-ws-a"));

    fireEvent.click(screen.getByTestId("submit"));

    await vi.waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(onRegistered).toHaveBeenCalledWith(
      { registrationId: "id-1", registeredAt: "t", type: "EXTERNAL" },
      "Ana",
      "Novak",
    );
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    expect(body.firstName).toBe("Ana");
    expect(body.optionIds).toEqual([]);
  });

  it("blocks submission without the anti-automation check or with too many options", async () => {
    const fetchMock = respond(201, {});
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("captcha-test"));
    fireEvent.click(screen.getByTestId("option-ws-a"));
    fireEvent.click(screen.getByTestId("option-ws-b"));

    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("error-captchaToken").textContent).toContain("not a robot");
    expect(screen.getByTestId("error-optionIds").textContent).toContain("at most 1");
    expect(screen.getByTestId("option-ws-a").getAttribute("aria-describedby")).toBe(
      "optionIds-error",
    );
    expect(screen.getByTestId("captcha-test").getAttribute("aria-describedby")).toBe(
      "captchaToken-error",
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a missing consent next to the consents", () => {
    respond(201, {});
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillExternal();
    fireEvent.click(screen.getByTestId("consent-data"));

    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("error-consentIds")).toBeTruthy();
    expect(screen.getByTestId("consent-data").getAttribute("aria-describedby")).toBe(
      "consentIds-error",
    );
  });

  it("shows server errors for unknown fields and known codes in the form error", async () => {
    respond(400, {
      error: "validation_failed",
      message: "m",
      fieldErrors: [
        { field: "type", code: "x", message: "Type problem." },
        { field: "email", code: "invalid_email", message: "Bad email." },
        { field: "email", code: "too_long", message: "Second email error." },
      ],
    });
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillExternal();

    fireEvent.click(screen.getByTestId("submit"));

    expect((await screen.findByTestId("form-error")).textContent).toBe("Type problem.");
    expect(screen.getByTestId("error-email").textContent).toBe("Bad email.");
  });

  it.each([
    [429, "rate_limited", "Too many attempts. Please wait a minute and try again."],
    [400, "captcha_failed", "Please confirm that you are not a robot."],
    [
      503,
      "captcha_unavailable",
      "The anti-automation check is unavailable. Please try again later.",
    ],
    [500, "internal_error", "The registration could not be processed. Please try again later."],
  ])("maps a %s %s answer to a fixed message", async (status, error, message) => {
    respond(status, { error, message: "server text", fieldErrors: [] });
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillExternal();

    fireEvent.click(screen.getByTestId("submit"));

    expect((await screen.findByTestId("form-error")).textContent).toBe(message);
    expect((screen.getByTestId("submit") as HTMLButtonElement).disabled).toBe(false);
  });
});

describe("registration page", () => {
  it("says so when the form data cannot be loaded", async () => {
    respond(503, {});
    render(<App />);

    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded");
  });

  it("shows the conference name once loaded", async () => {
    respond(200, form);
    render(<App />);

    expect((await screen.findByRole("heading")).textContent).toBe("Konferenca");
  });
});
