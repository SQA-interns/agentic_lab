import { screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MESSAGES,
  check,
  fillExternal,
  input,
  renderForm,
  stubApi,
  submit,
  waitForPost,
} from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

function completeForm() {
  fillExternal();
  check("consent");
  check("recaptcha");
}

describe("US-004 registration confirmation", () => {
  it("AC-004-01 shows the confirmation only after the registration was accepted", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const api = stubApi();
    const original = api.getMockImplementation()!;
    api.mockImplementation(async (url, init) => {
      if ((init?.method ?? "GET") === "POST") {
        await gate;
      }
      return original(url, init);
    });
    await renderForm();
    completeForm();
    submit();
    await waitForPost(api);

    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
    release();

    expect(await screen.findByTestId("confirmation")).toHaveTextContent(MESSAGES.CONFIRMATION);
    expect(screen.queryByTestId("registration-form")).not.toBeInTheDocument();
  });

  it("AC-004-02 shows server field errors at the fields and keeps the entered values", async () => {
    stubApi({
      status: 400,
      body: {
        title: "Validation failed",
        status: 400,
        code: "VALIDATION_FAILED",
        errors: [
          { field: "email", code: "INVALID_EMAIL" },
          { field: "lastName", code: "REQUIRED" },
        ],
      },
    });
    await renderForm();
    completeForm();
    submit();

    expect(await screen.findByTestId("error-email")).toHaveTextContent(MESSAGES.INVALID_EMAIL);
    expect(screen.getByTestId("error-lastName")).toHaveTextContent(MESSAGES.REQUIRED);
    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
    expect(input("firstName").value).toBe("Ana");
    expect(input("organization").value).toBe("Institut Jožef Stefan");
    expect(input("consent").checked).toBe(true);
  });

  it("AC-004-03 shows a general error without details when the service is unavailable", async () => {
    stubApi({
      status: 503,
      body: { title: "Service unavailable", status: 503, code: "SERVICE_UNAVAILABLE" },
    });
    await renderForm();
    completeForm();
    submit();

    const error = await screen.findByTestId("form-error");
    expect(error).toHaveTextContent(MESSAGES.GENERAL);
    expect(error).toHaveAttribute("role", "alert");
    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
    expect(input("firstName").value).toBe("Ana");
  });

  it("AC-004-03 shows a general error when the network fails", async () => {
    stubApi(new TypeError("Failed to fetch"));
    await renderForm();
    completeForm();
    submit();

    expect(await screen.findByTestId("form-error")).toHaveTextContent(MESSAGES.GENERAL);
    expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument();
    expect(screen.getByTestId("form-error")).not.toHaveTextContent("Failed to fetch");
  });

  it("AC-004-03 shows a general error for an unexpected server error", async () => {
    stubApi({
      status: 500,
      body: { title: "Internal error", status: 500, detail: "NullPointerException at x.y" },
    });
    await renderForm();
    completeForm();
    submit();

    const error = await screen.findByTestId("form-error");
    expect(error).toHaveTextContent(MESSAGES.GENERAL);
    expect(error).not.toHaveTextContent("NullPointerException");
    await waitFor(() => expect(screen.queryByTestId("confirmation")).not.toBeInTheDocument());
  });
});
