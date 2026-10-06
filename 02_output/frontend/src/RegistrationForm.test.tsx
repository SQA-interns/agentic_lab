import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import type { FormConfig } from "./api";
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

function fillAndSubmit() {
  for (const [label, value] of [
    ["First name", "Ana"],
    ["Last name", "Novak"],
    ["Email", "a@b.si"],
    ["Organization / institution", "IJS"],
  ]) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  fireEvent.click(screen.getByRole("checkbox", { name: "I agree" }));
  fireEvent.click(
    screen.getByRole("checkbox", { name: "I am not a robot (test mode)" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Register" }));
}

afterEach(() => {
  vi.unstubAllGlobals();
  delete window.grecaptcha;
});

describe("RegistrationForm", () => {
  it.each([
    [429, "Too many requests. Please wait a moment and try again."],
    [
      503,
      "The anti-automation check could not be completed. Please try again later.",
    ],
    [500, "Your registration was not saved. Please try again later."],
  ])("shows a fixed message for status %i", async (status, message) => {
    answer(status, { title: "x", detail: "internal" });
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);

    fillAndSubmit();

    expect(await screen.findByText(message)).toBeVisible();
    expect(screen.queryByText("internal")).toBeNull();
    expect(screen.getByRole("button", { name: "Register" })).toBeEnabled();
  });

  it("shows errors of fields that are not on the form above the button", async () => {
    answer(400, {
      errors: [
        {
          field: "optionIds",
          code: "INACTIVE_OPTION",
          message: "No longer offered.",
        },
      ],
    });
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);

    fillAndSubmit();

    expect(await screen.findByText("No longer offered.")).toBeVisible();
  });

  it("shows a backend captcha error next to the captcha", async () => {
    answer(400, {
      errors: [
        {
          field: "captchaToken",
          code: "CAPTCHA_FAILED",
          message: "Please confirm that you are not a robot.",
        },
      ],
    });
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);

    fillAndSubmit();

    const checkbox = screen.getByRole("checkbox", {
      name: "I am not a robot (test mode)",
    });
    await waitFor(() =>
      expect(checkbox).toHaveAttribute("aria-invalid", "true"),
    );
  });

  it("reports a network failure without details", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("Failed to fetch"))),
    );
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);

    fillAndSubmit();

    expect(
      await screen.findByText(
        "Your registration was not saved. Please try again later.",
      ),
    ).toBeVisible();
  });

  it("drops options the new type is not offered and sends the student fields only", async () => {
    const fetchMock = answer(201, { registrationId: "id", options: [] });
    const onAccepted = vi.fn();
    render(<RegistrationForm config={config} onAccepted={onAccepted} />);

    fireEvent.click(
      screen.getByRole("checkbox", { name: "External workshop" }),
    );
    fireEvent.click(screen.getByRole("checkbox", { name: "Lunch" }));
    fireEvent.click(screen.getByRole("radio", { name: "Student" }));
    for (const [label, value] of [
      ["First name", "L"],
      ["Last name", "K"],
      ["Email", "l@k.si"],
      ["Study institution", "UM"],
      ["Study programme", "P"],
      ["Student ID", "1"],
    ]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("checkbox", { name: "I agree" }));
    fireEvent.click(
      screen.getByRole("checkbox", { name: "I am not a robot (test mode)" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(onAccepted).toHaveBeenCalled());
    const body = JSON.parse(
      (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
        .body as string,
    );
    expect(body.optionIds).toEqual(["all"]);
    expect(body).toMatchObject({
      studyInstitution: "UM",
      studyProgramme: "P",
      studentId: "1",
    });
    expect(body).not.toHaveProperty("organization");
  });

  it("unchecking the test captcha clears the token", () => {
    render(<RegistrationForm config={config} onAccepted={vi.fn()} />);
    const checkbox = screen.getByRole("checkbox", {
      name: "I am not a robot (test mode)",
    });

    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });

  it("renders the live widget with the site key and uses its token", async () => {
    const render_ = vi.fn(
      (
        _: HTMLElement,
        p: { sitekey: string; callback: (t: string) => void },
      ) => {
        p.callback("live-token");
        return 1;
      },
    );
    window.grecaptcha = { render: render_ };
    const fetchMock = answer(201, { registrationId: "id", options: [] });
    render(
      <RegistrationForm
        config={{ ...config, captcha: { mode: "live", siteKey: "site-key" } }}
        onAccepted={vi.fn()}
      />,
    );

    expect(render_).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ sitekey: "site-key" }),
    );
    for (const [label, value] of [
      ["First name", "Ana"],
      ["Last name", "Novak"],
      ["Email", "a@b.si"],
      ["Organization / institution", "IJS"],
    ]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("checkbox", { name: "I agree" }));
    fireEvent.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const body = JSON.parse(
      (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1]
        .body as string,
    );
    expect(body.captchaToken).toBe("live-token");
  });

  it("loads the Google script once when the widget is not there yet", () => {
    render(
      <RegistrationForm
        config={{ ...config, captcha: { mode: "live", siteKey: "k" } }}
        onAccepted={vi.fn()}
      />,
    );

    const script = document.getElementById(
      "recaptcha-script",
    ) as HTMLScriptElement;
    expect(script.src).toContain("https://www.google.com/recaptcha/api.js");
    script.remove();
  });
});

describe("App", () => {
  it("says so when the form cannot be loaded", async () => {
    answer(500);
    render(<App />);

    expect(
      await screen.findByText(
        "The registration form could not be loaded. Please try again later.",
      ),
    ).toBeVisible();
  });

  it("shows the conference name and no options text in the confirmation", async () => {
    const fetchMock = vi.fn(async (url: string) =>
      url.includes("form-config")
        ? new Response(JSON.stringify(config), { status: 200 })
        : new Response(
            JSON.stringify({
              registrationId: "r-1",
              firstName: "Ana",
              lastName: "Novak",
              email: "a@b.si",
              options: [],
            }),
            { status: 201 },
          ),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);
    await screen.findByRole("button", { name: "Register" });
    expect(screen.getByText("C")).toBeVisible();

    fillAndSubmit();

    expect(await screen.findByText("No options selected.")).toBeVisible();
  });
});
