import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import {
  CONSENT_TEXT,
  TEST_MODE_CHECKBOX,
  check,
  chooseType,
  confirmConsentAndRobot,
  fakeBackend,
  fill,
  fillExternal,
  formConfig,
  renderApp,
  submit,
} from "./acceptance/support";

afterEach(() => vi.unstubAllGlobals());

describe("RegistrationForm", () => {
  it("refuses more workshops than the maximum before sending", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    check("Workshop: AI in research");
    check("Workshop: Open data");
    check("Workshop: Industry lab");
    confirmConsentAndRobot();

    submit();

    const group = screen.getByRole("group", { name: "Workshops" });
    await waitFor(() =>
      expect(group).toHaveAccessibleDescription("Too many options selected in this group."),
    );
    expect(backend.posted()).toHaveLength(0);
  });

  it("drops selected options that the new type cannot take", async () => {
    const backend = fakeBackend();
    await renderApp();
    check("Gala dinner");
    check("Welcome reception");
    chooseType("Student");
    chooseType("External participant");
    fillExternal();
    confirmConsentAndRobot();

    submit();

    await waitFor(() => expect(backend.posted()).toHaveLength(1));
    expect(backend.posted()[0]!.optionIds).toEqual(["ev-welcome"]);
  });

  it("sends trimmed values and only the boxes that are still checked", async () => {
    const backend = fakeBackend();
    await renderApp();
    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("First name")).toHaveAttribute("type", "text");
    fillExternal();
    fill("First name", "  Janez ");
    check("Workshop: AI in research");
    check("Lunch, day 1");
    check("Workshop: AI in research");
    check(CONSENT_TEXT);
    check(CONSENT_TEXT);
    check(TEST_MODE_CHECKBOX);
    expect(screen.getByRole("checkbox", { name: TEST_MODE_CHECKBOX })).toBeChecked();

    submit();

    await waitFor(() =>
      expect(screen.getByRole("checkbox", { name: CONSENT_TEXT })).toHaveAccessibleDescription(
        "This consent is required.",
      ),
    );
    check(CONSENT_TEXT);
    submit();
    await waitFor(() => expect(backend.posted()).toHaveLength(1));
    expect(backend.posted()[0]!.firstName).toBe("Janez");
    expect(backend.posted()[0]!.optionIds).toEqual(["meal-lunch-day1"]);
  });

  it("checks each field against its own maximum length", async () => {
    const backend = fakeBackend();
    await renderApp();
    chooseType("Student");
    fill("First name", "Ana");
    fill("Last name", "Horvat");
    fill("Email", "ana@example.com");
    fill("Study institution", "U");
    fill("Study programme", "P");
    fill("Student ID", "1".repeat(51));
    confirmConsentAndRobot();

    submit();

    await waitFor(() =>
      expect(screen.getByLabelText("Student ID")).toHaveAccessibleDescription(
        "This value is too long.",
      ),
    );
    expect(backend.posted()).toHaveLength(0);
  });

  it("requires every configured consent", async () => {
    const config = {
      ...formConfig,
      consents: [
        { id: "first", text: "First consent" },
        { id: "second", text: "Second consent" },
      ],
    };
    const fetchMock = vi.fn(async () => new Response(JSON.stringify(config), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await renderApp();
    fillExternal();
    check("First consent");
    check(TEST_MODE_CHECKBOX);

    submit();

    await waitFor(() =>
      expect(screen.getByRole("checkbox", { name: "Second consent" })).toHaveAccessibleDescription(
        "This consent is required.",
      ),
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("rejects control characters before sending", async () => {
    const backend = fakeBackend();
    await renderApp();
    fillExternal();
    fill("First name", "Ja\u0007nez");
    confirmConsentAndRobot();

    submit();

    await waitFor(() =>
      expect(screen.getByLabelText("First name")).toHaveAccessibleDescription(
        "This value contains characters that are not allowed.",
      ),
    );
    expect(backend.posted()).toHaveLength(0);
  });

  it("clears the anti-automation check after the backend rejects the token", async () => {
    fakeBackend({ status: 400, body: { code: "CAPTCHA_FAILED", message: "Check failed." } });
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent("Check failed.");
    expect(screen.getByRole("checkbox", { name: TEST_MODE_CHECKBOX })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: CONSENT_TEXT })).toBeChecked();
  });

  it("shows a load error when the form configuration is unavailable", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("{}", { status: 503 })),
    );
    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The registration form could not be loaded.",
    );
    expect(screen.queryByRole("button", { name: "Register" })).not.toBeInTheDocument();
  });

  it("disables the submit button while sending", async () => {
    let release: (r: Response) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) =>
        String(input).endsWith("/api/form-config")
          ? fakeBackendConfig()
          : new Promise<Response>((resolve) => (release = resolve)),
      ),
    );
    await renderApp();
    fillExternal();
    confirmConsentAndRobot();

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("button", { name: "Sending…" })).toBeDisabled();
    release(new Response("{}", { status: 500 }));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});

async function fakeBackendConfig(): Promise<Response> {
  const { formConfig } = await import("./acceptance/support");
  return new Response(JSON.stringify(formConfig), { status: 200 });
}
