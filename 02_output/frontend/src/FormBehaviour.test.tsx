import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import type { FormConfig } from "./api";
import { Captcha } from "./Captcha";
import { RegistrationForm } from "./RegistrationForm";

const config: FormConfig = {
  conferenceName: "C",
  options: [
    {
      id: "ext",
      name: "External workshop",
      category: "workshop",
      offeredTo: ["external"],
    },
    {
      id: "all",
      name: "Lunch",
      category: "meal",
      offeredTo: ["external", "student"],
    },
  ],
  consents: [{ id: "data", text: "I agree", mandatory: true }],
  captcha: { mode: "test" },
};

function answer(status: number, body: unknown = {}) {
  const fetchMock = vi.fn(
    async () => new Response(JSON.stringify(body), { status }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function sentBody(fetchMock: ReturnType<typeof answer>) {
  return JSON.parse(
    (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
      .body as string,
  );
}

function fillExternal() {
  for (const [label, value] of [
    ["First name", "Ana"],
    ["Last name", "Novak"],
    ["Email", "a@b.si"],
    ["Organization / institution", "IJS"],
  ]) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  fireEvent.click(screen.getByRole("checkbox", { name: "I agree" }));
}

const submit = () =>
  fireEvent.click(screen.getByRole("button", { name: "Register" }));
const robot = () =>
  screen.getByRole("checkbox", { name: "I am not a robot (test mode)" });

afterEach(() => {
  vi.unstubAllGlobals();
  delete window.grecaptcha;
  delete window.onRecaptchaLoaded;
  document.getElementById("recaptcha-script")?.remove();
});

describe("form state", () => {
  it("starts empty, without alerts, with an email input for the email", () => {
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);

    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByLabelText("First name")).toHaveValue("");
    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("First name")).toHaveAttribute("type", "text");
  });

  it("does not show a category without options for the chosen type", () => {
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    expect(screen.queryByRole("group", { name: "Events" })).toBeNull();
    expect(screen.getByRole("group", { name: "Meals" })).toBeVisible();

    fireEvent.click(screen.getByRole("radio", { name: "Student" }));

    expect(screen.queryByRole("group", { name: "Workshops" })).toBeNull();
  });

  it("an unchecked option is not sent", async () => {
    const fetchMock = answer(201, { registrationId: "id", options: [] });
    const onAccepted = vi.fn();
    render(<RegistrationForm config={config} onAccepted={onAccepted} />);
    fillExternal();
    fireEvent.click(screen.getByRole("checkbox", { name: "Lunch" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "Lunch" }));
    fireEvent.click(robot());
    submit();

    await waitFor(() => expect(onAccepted).toHaveBeenCalled());
    expect(sentBody(fetchMock).optionIds).toEqual([]);
  });

  it("switching type clears errors and forgets options the other type is not offered", () => {
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fireEvent.click(
      screen.getByRole("checkbox", { name: "External workshop" }),
    );
    fireEvent.click(screen.getByRole("checkbox", { name: "Lunch" }));
    submit();
    expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("radio", { name: "Student" }));
    expect(screen.queryByRole("alert")).toBeNull();
    fireEvent.click(
      screen.getByRole("radio", { name: "External participant" }),
    );

    expect(
      screen.getByRole("checkbox", { name: "External workshop" }),
    ).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Lunch" })).toBeChecked();
  });

  it("unchecking the test captcha means it must be confirmed again", async () => {
    const fetchMock = answer(201);
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(robot());
    fireEvent.click(robot());
    submit();

    expect(
      await screen.findByText("Please confirm that you are not a robot."),
    ).toBeVisible();
    expect(robot()).toHaveAttribute("aria-describedby", "captchaToken-error");
    expect(document.getElementById("captchaToken-error")).toHaveAttribute(
      "role",
      "alert",
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("disables the button while sending", async () => {
    let finish: (r: Response) => void = () => undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise<Response>((resolve) => (finish = resolve))),
    );
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(robot());
    submit();

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Register" })).toBeDisabled(),
    );
    finish(new Response("{}", { status: 500 }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Register" })).toBeEnabled(),
    );
  });

  it("clears an earlier general message when sending again", async () => {
    answer(429);
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(robot());
    submit();
    await screen.findByText(
      "Too many requests. Please wait a moment and try again.",
    );

    fireEvent.click(robot());
    submit();

    expect(
      screen.queryByText(
        "Too many requests. Please wait a moment and try again.",
      ),
    ).toBeNull();
  });

  it("shows backend consent errors at the consent and only field errors without a general message", async () => {
    answer(400, {
      errors: [
        {
          field: "consents.data",
          code: "CONSENT_REQUIRED",
          message: "This consent is required.",
        },
      ],
    });
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(robot());
    submit();

    const consent = screen.getByRole("checkbox", { name: "I agree" });
    await waitFor(() =>
      expect(consent).toHaveAttribute("aria-invalid", "true"),
    );
    expect(consent).toHaveAttribute("aria-describedby", "consent-data-error");
    expect(screen.getAllByRole("alert")).toHaveLength(1);
  });

  it("joins several general messages", async () => {
    answer(400, {
      errors: [
        { field: "optionIds", code: "INACTIVE_OPTION", message: "First." },
        { field: "consents", code: "UNKNOWN_CONSENT", message: "Second." },
      ],
    });
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    fillExternal();
    fireEvent.click(robot());
    submit();

    expect(await screen.findByText("First. Second.")).toBeVisible();
  });
});

describe("live captcha", () => {
  function widget() {
    const calls: {
      sitekey: string;
      callback: (t: string) => void;
      "expired-callback": () => void;
    }[] = [];
    window.grecaptcha = {
      render: vi.fn((target: HTMLElement, parameters) => {
        calls.push(parameters);
        target.appendChild(document.createElement("iframe"));
        return calls.length;
      }),
    };
    return calls;
  }

  it("does nothing in test mode or without a site key", () => {
    const calls = widget();
    render(<Captcha mode="test" token="" onToken={vi.fn()} />);
    render(<Captcha mode="live" token="" onToken={vi.fn()} />);

    expect(calls).toHaveLength(0);
    expect(document.getElementById("recaptcha-script")).toBeNull();
  });

  it("renders once even when its properties change, and expiry clears the token", () => {
    const calls = widget();
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha mode="live" siteKey="a" token="" onToken={onToken} />,
    );
    rerender(<Captcha mode="live" siteKey="b" token="" onToken={onToken} />);

    expect(calls).toHaveLength(1);
    calls[0].callback("token");
    calls[0]["expired-callback"]();
    expect(onToken.mock.calls).toEqual([["token"], [""]]);
  });

  it("renders when the Google script has loaded and adds the script only once", () => {
    const onToken = vi.fn();
    render(<Captcha mode="live" siteKey="k" token="" onToken={onToken} />);
    render(<Captcha mode="live" siteKey="k" token="" onToken={onToken} />);
    expect(document.querySelectorAll("#recaptcha-script")).toHaveLength(1);

    const calls = widget();
    window.onRecaptchaLoaded?.();

    expect(calls).toHaveLength(1);
    expect(calls[0].sitekey).toBe("k");
  });

  it("links its error to the widget", () => {
    widget();
    render(
      <Captcha
        mode="live"
        siteKey="k"
        token=""
        onToken={vi.fn()}
        error="Confirm."
      />,
    );

    expect(screen.getByRole("alert")).toHaveAttribute(
      "id",
      "captchaToken-error",
    );
  });
});

describe("App loading", () => {
  it("shows loading until the configuration arrives", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(config), { status: 200 })),
    );
    render(<App />);

    expect(screen.getByText("Loading…")).toBeVisible();
    expect(screen.queryByRole("alert")).toBeNull();
    await screen.findByRole("button", { name: "Register" });
  });
});
