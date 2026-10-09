import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { App } from "../App";
import { created, stubApi } from "./support/api-stub";
import { chooseType, fillValidExternal, submit } from "./support/form";

afterEach(() => {
  cleanup();
});

describe("US-004 Registration confirmation (UI)", () => {
  it("AC-004-01 an accepted registration shows the confirmation instead of the form", async () => {
    stubApi(created());
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();

    submit();

    const confirmation = await screen.findByTestId("confirmation");
    expect(confirmation.getAttribute("role")).toBe("status");
    expect(confirmation.textContent).toContain(
      "Thank you, Ana Novak. Your registration has been received.",
    );
    expect(confirmation.textContent).toContain("3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40");
    expect(screen.queryByTestId("registration-form")).toBeNull();
  });

  it("AC-004-02 a rejected registration shows each field error next to its field and keeps the values", async () => {
    stubApi({
      status: 400,
      body: {
        error: "validation_failed",
        message: "Some fields are invalid.",
        fieldErrors: [
          { field: "lastName", code: "too_long", message: "Last name is too long." },
          { field: "optionIds", code: "too_many_options", message: "Too many workshops." },
        ],
      },
    });
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();

    submit();

    const lastNameError = await screen.findByTestId("error-lastName");
    expect(lastNameError.textContent).toContain("Last name is too long.");
    expect(screen.getByTestId("field-lastName").getAttribute("aria-describedby")).toContain(
      lastNameError.id,
    );
    expect(screen.getByTestId("error-optionIds").textContent).toContain("Too many workshops.");
    expect(screen.queryByTestId("confirmation")).toBeNull();
    expect((screen.getByTestId("field-firstName") as HTMLInputElement).value).toBe("Ana");
    expect((screen.getByTestId("field-lastName") as HTMLInputElement).value).toBe("Novak");
  });

  it("AC-004-02 a duplicate email is shown next to the email field", async () => {
    stubApi({
      status: 409,
      body: {
        error: "duplicate_email",
        message: "This email is already registered.",
        fieldErrors: [
          { field: "email", code: "duplicate_email", message: "This email is already registered." },
        ],
      },
    });
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();

    submit();

    expect((await screen.findByTestId("error-email")).textContent).toContain(
      "This email is already registered.",
    );
    expect(screen.queryByTestId("confirmation")).toBeNull();
  });

  it("AC-004-03 a failed registration shows a general error without internal details", async () => {
    stubApi({
      status: 500,
      body: {
        error: "internal_error",
        message: "java.sql.SQLException: connection refused",
        fieldErrors: [],
      },
    });
    render(<App />);
    await chooseType("EXTERNAL");
    fillValidExternal();

    submit();

    const error = await screen.findByTestId("form-error");
    expect(error.getAttribute("role")).toBe("alert");
    expect(error.textContent).not.toBe("");
    expect(error.textContent).not.toContain("SQLException");
    expect(screen.queryByTestId("confirmation")).toBeNull();
    expect(screen.getByTestId("registration-form")).toBeTruthy();
  });
});
