import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { App } from "./App";
import { Confirmation } from "./Confirmation";
import { ConsentList } from "./ConsentList";
import { OptionGroups } from "./OptionGroups";
import { Recaptcha, TEST_MODE_TOKEN } from "./Recaptcha";
import { RegistrationForm } from "./RegistrationForm";
import type { ClientConfig, ConferenceOption, Consent, Registration } from "./types";

const OPTIONS: ConferenceOption[] = [
  { id: "ws", name: "AI workshop", category: "workshop" },
  { id: "meal", name: "Lunch", category: "meal" },
  { id: "tour", name: "Tour", category: "other" },
];
const CONSENTS: Consent[] = [
  { id: "privacy", text: "I agree to processing", required: true },
  { id: "news", text: "Send news", required: false },
];
const TEST_CONFIG: ClientConfig = {
  recaptchaSiteKey: "",
  recaptchaTestMode: true,
  conferenceName: "Konferenca",
};
const REGISTRATION: Registration = {
  id: "r-1",
  type: "EXTERNAL",
  submittedAt: "2026-09-30T10:00:00Z",
  firstName: "Ana",
  lastName: "Novak",
  email: "ana@example.si",
  organization: "IJS",
  options: [OPTIONS[0]],
  consents: [{ id: "privacy", givenAt: "2026-09-30T10:00:00Z" }],
};

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.getElementById("recaptcha-script")?.remove();
  delete window.grecaptcha;
});

function renderForm(onRegistered = vi.fn(), config = TEST_CONFIG) {
  render(
    <RegistrationForm
      config={config}
      options={OPTIONS}
      consents={CONSENTS}
      onRegistered={onRegistered}
    />,
  );
  return onRegistered;
}

function fillExternal() {
  fireEvent.change(screen.getByLabelText("First name"), { target: { value: "Ana" } });
  fireEvent.change(screen.getByLabelText("Last name"), { target: { value: "Novak" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "ana@example.si" } });
  fireEvent.change(screen.getByLabelText("Organization / institution"), {
    target: { value: "IJS" },
  });
  fireEvent.click(screen.getByLabelText("I agree to processing (required)"));
}

describe("OptionGroups", () => {
  it("groups options under category headings and skips empty categories", () => {
    const onToggle = vi.fn();
    render(<OptionGroups options={OPTIONS} selected={["meal"]} onToggle={onToggle} />);

    const headings = screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent);
    expect(headings).toEqual(["Workshops", "Meals", "Other activities"]);
    const meals = screen.getByRole("region", { name: "Meals" });
    expect(within(meals).getByLabelText("Lunch")).toBeChecked();
    fireEvent.click(screen.getByLabelText("AI workshop"));
    expect(onToggle).toHaveBeenCalledWith("ws");
  });

  it("shows an option error linked to the group", () => {
    render(<OptionGroups options={OPTIONS} selected={[]} onToggle={vi.fn()} error="Not here" />);

    expect(screen.getByText("Not here")).toHaveAttribute("id", "optionIds-error");
    expect(screen.getByRole("group", { name: "Options" })).toHaveAttribute(
      "aria-describedby",
      "optionIds-error",
    );
  });
});

describe("ConsentList", () => {
  it("renders unchecked consents and marks required ones", () => {
    const onToggle = vi.fn();
    render(<ConsentList consents={CONSENTS} given={[]} onToggle={onToggle} />);

    expect(screen.getByLabelText("I agree to processing (required)")).not.toBeChecked();
    expect(screen.getByLabelText("Send news")).not.toBeChecked();
    fireEvent.click(screen.getByLabelText("Send news"));
    expect(onToggle).toHaveBeenCalledWith("news");
  });
});

describe("Recaptcha", () => {
  it("emits the pass token in test mode and loads nothing", () => {
    const onToken = vi.fn();
    render(<Recaptcha siteKey="" testMode resetCount={0} onToken={onToken} />);

    expect(onToken).toHaveBeenCalledWith(TEST_MODE_TOKEN);
    expect(screen.getByText("reCAPTCHA test mode")).toBeInTheDocument();
    expect(document.getElementById("recaptcha-script")).toBeNull();
  });

  it("loads the Google script once outside test mode and renders the widget", () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Recaptcha siteKey="site" testMode={false} resetCount={0} onToken={onToken} />,
    );

    const script = document.getElementById("recaptcha-script") as HTMLScriptElement;
    expect(script.src).toContain("https://www.google.com/recaptcha/api.js");
    const renderWidget = vi.fn().mockReturnValue(7);
    const reset = vi.fn();
    window.grecaptcha = { render: renderWidget, reset };
    window.onRecaptchaLoaded?.();
    expect(renderWidget).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ sitekey: "site" }),
    );
    const options = renderWidget.mock.calls[0][1];
    options.callback("real-token");
    expect(onToken).toHaveBeenCalledWith("real-token");
    options["expired-callback"]();
    expect(onToken).toHaveBeenLastCalledWith("");

    rerender(<Recaptcha siteKey="site" testMode={false} resetCount={1} onToken={onToken} />);
    expect(reset).toHaveBeenCalledWith(7);
  });

  it("renders at once when the script is already loaded", () => {
    const renderWidget = vi.fn().mockReturnValue(1);
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };

    render(<Recaptcha siteKey="k" testMode={false} resetCount={0} onToken={vi.fn()} error="x" />);

    expect(renderWidget).toHaveBeenCalledTimes(1);
    expect(screen.getByText("x")).toHaveAttribute("id", "recaptchaToken-error");
  });
});

describe("RegistrationForm", () => {
  it("shows the external fields by default and the student fields after switching", () => {
    renderForm();
    const details = screen.getByRole("group", { name: "Your details" });
    expect(
      within(details)
        .getAllByRole("textbox")
        .map((e) => e.getAttribute("id")),
    ).toEqual(["firstName", "lastName", "email", "organization"]);

    fireEvent.change(screen.getByLabelText("First name"), { target: { value: "Luka" } });
    fireEvent.click(screen.getByLabelText("Student"));

    expect(screen.getByLabelText("First name")).toHaveValue("Luka");
    expect(screen.queryByLabelText("Organization / institution")).toBeNull();
    expect(screen.getByLabelText("Student ID")).toBeInTheDocument();
  });

  it("validates before sending and links errors to fields", async () => {
    const fetchFn = vi.fn();
    vi.stubGlobal("fetch", fetchFn);
    renderForm();

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText("Enter a valid email address.")).toHaveAttribute(
      "id",
      "email-error",
    );
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-describedby", "email-error");
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getAllByText("This field is required.")).toHaveLength(3);
    expect(screen.getByText("This consent is required.")).toHaveAttribute("id", "consents-error");
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("requires a reCAPTCHA token outside test mode", async () => {
    const fetchFn = vi.fn();
    vi.stubGlobal("fetch", fetchFn);
    window.grecaptcha = { render: vi.fn().mockReturnValue(0), reset: vi.fn() };
    renderForm(vi.fn(), { ...TEST_CONFIG, recaptchaTestMode: false, recaptchaSiteKey: "k" });
    fillExternal();

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText("Confirm that you are not a robot.")).toBeInTheDocument();
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("submits the external form with options in display order", async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(201, REGISTRATION));
    vi.stubGlobal("fetch", fetchFn);
    const onRegistered = renderForm();
    fillExternal();
    fireEvent.click(screen.getByLabelText("Tour"));
    fireEvent.click(screen.getByLabelText("AI workshop"));

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(onRegistered).toHaveBeenCalledWith(REGISTRATION));
    const body = JSON.parse(fetchFn.mock.calls[0][1].body);
    expect(body).toEqual({
      type: "EXTERNAL",
      firstName: "Ana",
      lastName: "Novak",
      email: "ana@example.si",
      organization: "IJS",
      optionIds: ["ws", "tour"],
      consents: ["privacy"],
      recaptchaToken: TEST_MODE_TOKEN,
    });
  });

  it("unchecking an option removes it", async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(201, REGISTRATION));
    vi.stubGlobal("fetch", fetchFn);
    renderForm();
    fillExternal();
    fireEvent.click(screen.getByLabelText("Lunch"));
    fireEvent.click(screen.getByLabelText("Lunch"));

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(fetchFn).toHaveBeenCalled());
    expect(JSON.parse(fetchFn.mock.calls[0][1].body).optionIds).toEqual([]);
  });

  it("shows server field errors next to the field and keeps the values", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse(409, { fieldErrors: [{ field: "email", message: "Already registered." }] }),
        ),
    );
    const onRegistered = renderForm();
    fillExternal();

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText("Already registered.")).toHaveAttribute("id", "email-error");
    expect(screen.getByLabelText("First name")).toHaveValue("Ana");
    expect(onRegistered).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Register" })).toBeEnabled();
  });

  it("shows a general alert for other failures", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(500, {})));
    renderForm();
    fillExternal();

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("could not be sent");
  });

  it("disables the button while submitting", async () => {
    let resolve: (r: Response) => void = () => {};
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise<Response>((r) => (resolve = r))));
    renderForm();
    fillExternal();

    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("button", { name: "Submitting…" })).toBeDisabled();
    resolve(jsonResponse(201, REGISTRATION));
    await waitFor(() => expect(screen.getByRole("button")).toHaveTextContent("Register"));
  });
});

describe("Confirmation", () => {
  it("shows name, id, options and email", () => {
    render(<Confirmation registration={REGISTRATION} />);

    expect(screen.getByRole("heading", { name: "Registration received" })).toBeInTheDocument();
    expect(screen.getByText(/Thank you, Ana Novak\. Your registration ID is r-1\./)).toBeTruthy();
    expect(
      within(screen.getByRole("list", { name: "Selected options" })).getByText("AI workshop"),
    ).toBeInTheDocument();
    expect(screen.getByText("A confirmation email has been sent to ana@example.si.")).toBeTruthy();
  });

  it("says when no options were selected", () => {
    render(<Confirmation registration={{ ...REGISTRATION, options: [] }} />);

    expect(screen.getByText("No options selected.")).toBeInTheDocument();
    expect(screen.queryByRole("list")).toBeNull();
  });
});

describe("App", () => {
  it("loads configuration and options, then shows the confirmation after acceptance", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        Promise.resolve(
          url === "/api/config"
            ? jsonResponse(200, TEST_CONFIG)
            : url === "/api/options"
              ? jsonResponse(200, { options: OPTIONS, consents: CONSENTS })
              : jsonResponse(201, REGISTRATION),
        ),
      ),
    );
    render(<App />);
    expect(screen.getByText("Loading…")).toBeInTheDocument();

    expect(
      await screen.findByRole("heading", { name: "Registration: Konferenca" }),
    ).toBeInTheDocument();
    fillExternal();
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("heading", { name: "Registration received" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Register" })).toBeNull();
  });

  it("explains when the form cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(503, {})));
    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("not available");
  });
});
