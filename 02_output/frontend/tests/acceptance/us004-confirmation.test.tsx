// US-004 Registration confirmation, through the rendered UI (ui-form.json, NFR-03).
import { cleanup, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fillValidExternal, openForm, submit } from "./helpers";
import { accepted, mockApi, problem } from "./support";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("US-004 confirmation", () => {
  it("AC-004-01 the confirmation appears only after the registration was accepted", async () => {
    let release: (value: ReturnType<typeof accepted>) => void = () => {};
    const pending = new Promise<ReturnType<typeof accepted>>((resolve) => (release = resolve));
    mockApi(pending);
    await openForm();
    await fillValidExternal();
    await submit();

    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByTestId("confirmation")).toBeNull();
    release(accepted());
    expect(await screen.findByTestId("confirmation")).toHaveTextContent(
      "Thank you, your registration was received.",
    );
    expect(screen.queryByTestId("submit")).toBeNull();
  });

  it("AC-004-02 backend field errors appear next to the fields and no confirmation is shown", async () => {
    mockApi(
      problem(400, "Invalid registration", [
        { field: "email", code: "invalid_email" },
        { field: "optionIds", code: "inactive_option" },
      ]),
    );
    await openForm();
    await fillValidExternal();
    await submit();

    expect(await screen.findByTestId("error-email")).toBeVisible();
    expect(await screen.findByTestId("error-optionIds")).toBeVisible();
    expect(screen.queryByTestId("confirmation")).toBeNull();
  });

  it("AC-004-02 a server failure shows a generic error without internal details", async () => {
    mockApi(problem(500, "Registration could not be processed"));
    await openForm();
    await fillValidExternal();
    await submit();

    const error = await screen.findByTestId("form-error");
    expect(error).toHaveTextContent("Registration could not be processed");
    await waitFor(() => expect(screen.queryByTestId("confirmation")).toBeNull());
    expect(screen.getByTestId("field-firstName")).toHaveValue("Ana");
  });
});
