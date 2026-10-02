// Unit tests of form behaviour the acceptance tests do not reach: option changes, what is sent,
// and what happens after the backend refused a registration (SR-01, NFR-03).
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ConferenceOption, FormConfig } from "./api";
import { RegistrationForm } from "./RegistrationForm";

const CONFIG: FormConfig = {
  conferenceName: "Konferenca",
  consent: { id: "personal-data", text: "Soglašam." },
  captcha: { mode: "test", siteKey: "" },
};
const OPTIONS: ConferenceOption[] = [
  { id: "w1", name: "Delavnica A", category: "workshop" },
  { id: "w2", name: "Delavnica B", category: "workshop" },
  { id: "m1", name: "Kosilo", category: "meal" },
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** Answers every registration with the given status and body and records the request bodies. */
function backend(status: number, body: unknown = {}) {
  const sent: Record<string, unknown>[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn<(input: string, init?: RequestInit) => Promise<Response>>((_input, init) => {
      sent.push(JSON.parse(init?.body as string) as Record<string, unknown>);
      return Promise.resolve(
        new Response(JSON.stringify(body), {
          status,
          headers: { "Content-Type": "application/json" },
        }),
      );
    }),
  );
  return sent;
}

function field(name: RegExp): HTMLInputElement {
  return screen.getByRole<HTMLInputElement>("textbox", { name });
}

function box(name: string | RegExp): HTMLInputElement {
  return screen.getByRole<HTMLInputElement>("checkbox", { name });
}

function openExternalForm(onAccepted: () => void = () => undefined) {
  render(<RegistrationForm config={CONFIG} options={OPTIONS} onAccepted={onAccepted} />);
  fireEvent.click(screen.getByRole("radio", { name: "Zunanji udeleženec" }));
}

function fillValidExternal() {
  fireEvent.change(field(/^Ime/), { target: { value: "  Živa " } });
  fireEvent.change(field(/^Priimek/), { target: { value: "Čučnik" } });
  fireEvent.change(field(/^E-pošta/), { target: { value: " ziva@example.org" } });
  fireEvent.change(field(/^Organizacija/), { target: { value: "Inštitut  " } });
  fireEvent.click(box("Soglašam."));
  fireEvent.click(box(/^Nisem robot/));
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Oddaj prijavo" }));
}

function rejection(errors: { field: string; code: string; message: string }[]) {
  return { type: "about:blank", title: "x", status: 400, errors };
}

describe("RegistrationForm", () => {
  it("sends trimmed values and the selected options in form order, without unticked ones", async () => {
    const sent = backend(201, { id: "x", acceptedAt: "2026-10-02T10:15:30Z" });
    const onAccepted = vi.fn();
    openExternalForm(onAccepted);
    fillValidExternal();
    fireEvent.click(box("Kosilo"));
    fireEvent.click(box("Delavnica B"));
    fireEvent.click(box("Delavnica A"));
    fireEvent.click(box("Delavnica B"));
    expect(box("Delavnica B")).not.toBeChecked();

    submit();

    await waitFor(() => expect(onAccepted).toHaveBeenCalledTimes(1));
    expect(sent).toEqual([
      {
        type: "EXTERNAL",
        firstName: "Živa",
        lastName: "Čučnik",
        email: "ziva@example.org",
        organization: "Inštitut",
        optionIds: ["w1", "m1"],
        consent: true,
        captchaToken: "test-pass",
      },
    ]);
  });

  it("SR-01 asks for a new anti-automation check after the backend refused a registration", async () => {
    const sent = backend(
      400,
      rejection([{ field: "email", code: "invalid_format", message: "Neveljaven naslov." }]),
    );
    const onAccepted = vi.fn();
    openExternalForm(onAccepted);
    fillValidExternal();

    submit();

    expect(await screen.findByText("Neveljaven naslov.")).toBeVisible();
    expect(box(/^Nisem robot/)).not.toBeChecked();
    expect(onAccepted).not.toHaveBeenCalled();

    submit();

    await waitFor(() =>
      expect(box(/^Nisem robot/)).toHaveAccessibleDescription("Potrdite, da niste robot."),
    );
    expect(box(/^Nisem robot/)).toBeInvalid();
    expect(sent).toHaveLength(1);
  });

  it("marks the consent as invalid when it is missing and clears the mark when the type changes", async () => {
    backend(201);
    openExternalForm();
    fireEvent.click(box(/^Nisem robot/));

    submit();

    await waitFor(() => expect(box("Soglašam.")).toBeInvalid());
    expect(box("Soglašam.")).toHaveAccessibleDescription("Za prijavo je potrebno soglasje.");
    expect(field(/^Ime/)).toBeInvalid();

    fireEvent.click(screen.getByRole("radio", { name: "Študent" }));

    expect(box("Soglašam.")).not.toBeInvalid();
    expect(field(/^Ime/)).not.toBeInvalid();
  });

  it("falls back to its own text when the backend sends a code without a message", async () => {
    backend(
      400,
      rejection([
        { field: "firstName", code: "too_long", message: "" },
        { field: "lastName", code: "some_new_code", message: "" },
      ]),
    );
    openExternalForm();
    fillValidExternal();

    submit();

    await waitFor(() => expect(field(/^Ime/)).toHaveAccessibleDescription("Vnos je predolg."));
    expect(field(/^Priimek/)).toHaveAccessibleDescription("Vrednost ni veljavna.");
  });

  it("reports an error for a field that is not on the form as not received", async () => {
    backend(400, rejection([{ field: "body", code: "unknown_field", message: "Polje." }]));
    const onAccepted = vi.fn();
    openExternalForm(onAccepted);
    fillValidExternal();

    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Prijave nismo prejeli. Poskusite znova pozneje.",
    );
    expect(screen.queryByText("Polje.")).toBeNull();
    expect(field(/^Ime/)).not.toBeInvalid();
    expect(onAccepted).not.toHaveBeenCalled();
  });

  it("clears an earlier failure message when the next attempt starts", async () => {
    backend(503);
    openExternalForm();
    fillValidExternal();
    submit();
    expect(await screen.findByRole("alert")).toBeVisible();

    submit();

    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
    expect(box(/^Nisem robot/)).toBeInvalid();
  });
});

describe("RegistrationForm with the reCAPTCHA widget", () => {
  afterEach(() => {
    delete window.grecaptcha;
  });

  it("SR-01 resets the widget after a refused registration, because a token is valid only once", async () => {
    let giveToken: (token: string) => void = () => undefined;
    const recaptcha = {
      ready: vi.fn((callback: () => void) => callback()),
      render: vi.fn((_container: HTMLElement, options: { callback: (token: string) => void }) => {
        giveToken = options.callback;
        return 11;
      }),
      reset: vi.fn(),
    };
    window.grecaptcha = recaptcha as unknown as Window["grecaptcha"];
    const sent = backend(
      400,
      rejection([{ field: "captchaToken", code: "captcha_failed", message: "Potrdite." }]),
    );
    render(
      <RegistrationForm
        config={{ ...CONFIG, captcha: { mode: "recaptcha", siteKey: "site-key" } }}
        options={OPTIONS}
        onAccepted={() => undefined}
      />,
    );
    fireEvent.click(screen.getByRole("radio", { name: "Zunanji udeleženec" }));
    fireEvent.change(field(/^Ime/), { target: { value: "Živa" } });
    fireEvent.change(field(/^Priimek/), { target: { value: "Čučnik" } });
    fireEvent.change(field(/^E-pošta/), { target: { value: "ziva@example.org" } });
    fireEvent.change(field(/^Organizacija/), { target: { value: "Inštitut" } });
    fireEvent.click(box("Soglašam."));
    giveToken("token-from-google");
    await waitFor(() => expect(recaptcha.render).toHaveBeenCalledTimes(1));
    expect(recaptcha.reset).not.toHaveBeenCalled();

    submit();

    expect(await screen.findByText("Potrdite.")).toBeVisible();
    expect(sent[0]?.captchaToken).toBe("token-from-google");
    await waitFor(() => expect(recaptcha.reset).toHaveBeenCalledTimes(1));
    expect(recaptcha.reset).toHaveBeenCalledWith(11);
  });
});
