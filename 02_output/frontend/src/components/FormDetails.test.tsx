import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { submitRegistration } from "../api";
import { Captcha } from "./Captcha";
import { RegistrationForm } from "./RegistrationForm";
import type { RegistrationFormData } from "../types";

// Details that the first mutation run showed untested.

const form: RegistrationFormData = {
  conferenceName: "C",
  captcha: { mode: "test", siteKey: null },
  categories: [
    {
      category: "workshop",
      maxSelections: 2,
      options: [{ id: "ws-a", name: "A", availableTo: ["EXTERNAL"] }],
    },
  ],
  consents: [{ id: "data", text: "I agree", mandatory: true }],
};

function stubFetch(status: number, body: unknown) {
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

function fillAll() {
  fireEvent.click(screen.getByTestId("type-external"));
  fireEvent.change(screen.getByTestId("field-firstName"), { target: { value: "Ana" } });
  fireEvent.change(screen.getByTestId("field-lastName"), { target: { value: "Novak" } });
  fireEvent.change(screen.getByTestId("field-email"), { target: { value: "ana@example.si" } });
  fireEvent.change(screen.getByTestId("field-organization"), { target: { value: "IJS" } });
  fireEvent.click(screen.getByTestId("consent-data"));
  fireEvent.click(screen.getByTestId("captcha-test"));
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  delete window.grecaptcha;
  delete window.onRecaptchaLoad;
  document.head.querySelectorAll("script").forEach((script) => script.remove());
});

describe("form details", () => {
  it("unchecks an option on a second click", () => {
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fireEvent.click(screen.getByTestId("type-external"));
    const option = screen.getByTestId("option-ws-a") as HTMLInputElement;

    fireEvent.click(option);
    expect(option.checked).toBe(true);
    fireEvent.click(option);
    expect(option.checked).toBe(false);
  });

  it("reports fields that were never touched as required", () => {
    const fetchMock = stubFetch(201, {});
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fireEvent.click(screen.getByTestId("type-external"));

    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("error-firstName").textContent).toBe("First name is required.");
    expect(screen.getByTestId("error-organization").textContent).toBe(
      "Organization / institution is required.",
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sends a registration only once while the first submission is pending", async () => {
    let resolve: (value: Response) => void = () => {};
    const fetchMock = vi.fn(() => new Promise<Response>((r) => (resolve = r)));
    vi.stubGlobal("fetch", fetchMock);
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillAll();

    fireEvent.click(screen.getByTestId("submit"));
    fireEvent.submit(screen.getByTestId("registration-form"));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect((screen.getByTestId("submit") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByTestId("submit").textContent).toBe("Sending…");
    resolve(
      new Response(JSON.stringify({ error: "x", message: "m", fieldErrors: [] }), { status: 500 }),
    );
    await screen.findByTestId("form-error");
  });

  it("shows no general error when every server error belongs to a field", async () => {
    stubFetch(400, {
      error: "validation_failed",
      message: "m",
      fieldErrors: [{ field: "firstName", code: "too_long", message: "Too long." }],
    });
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillAll();

    fireEvent.click(screen.getByTestId("submit"));

    const error = await screen.findByTestId("error-firstName");
    expect(error.id).toBe("firstName-error");
    expect(screen.queryByTestId("form-error")).toBeNull();
  });

  it("joins several general errors and gives list errors their ids", async () => {
    stubFetch(400, {
      error: "validation_failed",
      message: "m",
      fieldErrors: [
        { field: "type", code: "x", message: "One." },
        { field: "unknown", code: "x", message: "Two." },
        { field: "consentIds", code: "consent_required", message: "Consent." },
      ],
    });
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fillAll();

    fireEvent.click(screen.getByTestId("submit"));

    expect((await screen.findByTestId("form-error")).textContent).toBe("One. Two.");
    expect(screen.getByTestId("error-consentIds").id).toBe("consentIds-error");
  });

  it("resets the reCAPTCHA widget after a submission the server refused", async () => {
    stubFetch(400, { error: "captcha_failed", message: "m", fieldErrors: [] });
    const reset = vi.fn();
    let callback: (token: string) => void = () => {};
    window.grecaptcha = {
      render: vi.fn((_c: HTMLElement, p: { callback: (t: string) => void }) => {
        callback = p.callback;
        return 5;
      }),
      reset,
    };
    render(
      <RegistrationForm
        form={{ ...form, captcha: { mode: "recaptcha", siteKey: "k" } }}
        onRegistered={() => {}}
      />,
    );
    fireEvent.click(screen.getByTestId("type-external"));
    fireEvent.change(screen.getByTestId("field-firstName"), { target: { value: "Ana" } });
    fireEvent.change(screen.getByTestId("field-lastName"), { target: { value: "Novak" } });
    fireEvent.change(screen.getByTestId("field-email"), { target: { value: "ana@example.si" } });
    fireEvent.change(screen.getByTestId("field-organization"), { target: { value: "IJS" } });
    fireEvent.click(screen.getByTestId("consent-data"));
    act(() => callback("token-from-google"));

    fireEvent.click(screen.getByTestId("submit"));

    await screen.findByTestId("form-error");
    // The reset runs in a passive effect, possibly after the error is on screen.
    await vi.waitFor(() => expect(reset).toHaveBeenCalledWith(5));
  });
});

describe("captcha details", () => {
  it("does not reset on first render or before the widget exists", () => {
    const reset = vi.fn();
    const props = {
      mode: "recaptcha" as const,
      siteKey: "k",
      token: "",
      invalid: false,
      onToken: vi.fn(),
    };
    const { rerender } = render(<Captcha {...props} resetSignal={0} />);
    expect(reset).not.toHaveBeenCalled();

    rerender(<Captcha {...props} resetSignal={1} />);
    expect(props.onToken).not.toHaveBeenCalled();

    window.grecaptcha = { render: vi.fn(() => 0), reset };
    window.onRecaptchaLoad!();
    rerender(<Captcha {...props} resetSignal={2} />);
    expect(reset).toHaveBeenCalledWith(0);
  });

  it("renders the widget once and adds the script once, asynchronously", () => {
    const props = {
      mode: "recaptcha" as const,
      siteKey: "k",
      token: "",
      resetSignal: 0,
      invalid: false,
      onToken: vi.fn(),
    };
    const first = render(<Captcha {...props} />);
    first.unmount();
    render(<Captcha {...props} />);

    const scripts = document.head.querySelectorAll("script");
    expect(scripts).toHaveLength(1);
    expect((scripts[0] as HTMLScriptElement).async).toBe(true);
    expect((scripts[0] as HTMLScriptElement).defer).toBe(true);

    const renderWidget = vi.fn(() => 1);
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    window.onRecaptchaLoad!();
    window.onRecaptchaLoad!();
    expect(renderWidget).toHaveBeenCalledTimes(1);
  });

  it("does not render before Google's script has loaded", () => {
    render(
      <Captcha
        mode="recaptcha"
        siteKey={null}
        token=""
        resetSignal={0}
        invalid={false}
        onToken={vi.fn()}
      />,
    );

    expect(() => window.onRecaptchaLoad!()).not.toThrow();
  });

  it("passes an empty site key when none is configured", () => {
    const sitekeys: string[] = [];
    const renderWidget = vi.fn((...args: [HTMLElement, { sitekey: string }]) => {
      sitekeys.push(args[1].sitekey);
      return 1;
    });
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };

    render(
      <Captcha
        mode="recaptcha"
        siteKey={null}
        token=""
        resetSignal={0}
        invalid={false}
        onToken={vi.fn()}
      />,
    );

    expect(sitekeys).toEqual([""]);
  });
});

describe("api details", () => {
  it("treats field errors of other statuses as a failure", async () => {
    stubFetch(500, {
      error: "internal_error",
      message: "m",
      fieldErrors: [{ field: "email", code: "x", message: "m" }],
    });

    expect(
      await submitRegistration({
        type: "EXTERNAL",
        optionIds: [],
        consentIds: [],
        captchaToken: "",
      }),
    ).toEqual({
      kind: "failed",
      errorCode: "internal_error",
    });
  });

  it("treats a JSON string answer as a failure without a code", async () => {
    stubFetch(500, "oops");

    expect(
      await submitRegistration({
        type: "EXTERNAL",
        optionIds: [],
        consentIds: [],
        captchaToken: "",
      }),
    ).toEqual({
      kind: "failed",
      errorCode: null,
    });
  });
});
