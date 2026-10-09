import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { submitRegistration } from "../api";
import { textFieldError } from "../validation";
import { Captcha } from "./Captcha";
import { Confirmation } from "./Confirmation";
import { RegistrationForm } from "./RegistrationForm";
import type { RegistrationFormData } from "../types";

// Gaps left by the second mutation run.

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  delete window.grecaptcha;
});

const form: RegistrationFormData = {
  conferenceName: "C",
  captcha: { mode: "test", siteKey: null },
  categories: [],
  consents: [{ id: "data", text: "I agree", mandatory: true }],
};

describe("mutation gaps", () => {
  it("rejects emails with anything after the domain", () => {
    expect(textFieldError("email", "ana@example.si@x")).toBe("Enter a valid email address.");
    expect(textFieldError("email", "ana@example.si x")).toBe("Enter a valid email address.");
  });

  it("treats a 201 with an empty body as a failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("null", { status: 201 })),
    );

    expect(
      await submitRegistration({
        type: "STUDENT",
        optionIds: [],
        consentIds: [],
        captchaToken: "",
      }),
    ).toEqual({ kind: "failed", errorCode: null });
  });

  it("clears an earlier server error when the next attempt fails client validation", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(JSON.stringify({ error: "rate_limited", message: "m", fieldErrors: [] }), {
            status: 429,
          }),
      ),
    );
    render(<RegistrationForm form={form} onRegistered={() => {}} />);
    fireEvent.click(screen.getByTestId("type-external"));
    fireEvent.change(screen.getByTestId("field-firstName"), { target: { value: "Ana" } });
    fireEvent.change(screen.getByTestId("field-lastName"), { target: { value: "Novak" } });
    fireEvent.change(screen.getByTestId("field-email"), { target: { value: "ana@example.si" } });
    fireEvent.change(screen.getByTestId("field-organization"), { target: { value: "IJS" } });
    fireEvent.click(screen.getByTestId("consent-data"));
    fireEvent.click(screen.getByTestId("captcha-test"));
    fireEvent.click(screen.getByTestId("submit"));
    await screen.findByTestId("form-error");

    fireEvent.click(screen.getByTestId("consent-data"));
    fireEvent.click(screen.getByTestId("submit"));

    expect(screen.getByTestId("error-consentIds")).toBeTruthy();
    expect(screen.queryByTestId("form-error")).toBeNull();
  });

  it("does not reset a rendered widget before any submission", () => {
    const reset = vi.fn();
    window.grecaptcha = { render: vi.fn(() => 9), reset };

    render(
      <Captcha
        mode="recaptcha"
        siteKey="k"
        token=""
        resetSignal={0}
        invalid={false}
        onToken={vi.fn()}
      />,
    );

    expect(reset).not.toHaveBeenCalled();
  });

  it("labels the registration ID in the confirmation", () => {
    render(<Confirmation firstName="Ana" lastName="Novak" registrationId="id-1" />);

    expect(screen.getByTestId("confirmation").textContent).toContain("Registration ID: id-1");
  });
});
