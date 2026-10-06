import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const SETUP = {
  conferenceName: "Konf",
  consent: { id: "c", text: "I agree" },
  recaptcha: { testMode: false, siteKey: "site-key" },
  options: [{ id: "a", name: "A", category: "MEAL", offeredTo: ["EXTERNAL"] }],
};

afterEach(() => {
  vi.unstubAllGlobals();
  delete window.grecaptcha;
  document.head.querySelectorAll("script").forEach((s) => s.remove());
});

describe("App", () => {
  it("offers a retry when the form setup cannot be loaded", async () => {
    const f = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce(new Response(JSON.stringify(SETUP), { status: 200 }));
    vi.stubGlobal("fetch", f);
    render(<App />);

    expect(await screen.findByTestId("load-error")).toHaveTextContent(
      "The registration form could not be loaded.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByTestId("registration-form")).toBeInTheDocument();
    expect(screen.getByRole("heading")).toHaveTextContent("Konf: registration");
  });

  it("loads the Google widget with the configured site key outside test mode", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(SETUP), { status: 200 })),
    );
    render(<App />);
    await screen.findByTestId("registration-form");

    const script = document.head.querySelector("script");
    expect(script?.src).toContain("https://www.google.com/recaptcha/api.js");
    const render_ = vi.fn();
    window.grecaptcha = { render: render_ };
    window.onRecaptchaLoaded?.();
    expect(render_).toHaveBeenCalledWith(
      screen.getByTestId("recaptcha"),
      expect.objectContaining({ sitekey: "site-key" }),
    );
    expect(screen.getByTestId("options-WORKSHOP")).toHaveTextContent("No options offered.");
  });

  it("renders the widget directly when the script is already present", async () => {
    const render_ = vi.fn();
    window.grecaptcha = { render: render_ };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(SETUP), { status: 200 })),
    );
    render(<App />);
    await screen.findByTestId("registration-form");

    expect(render_).toHaveBeenCalledTimes(1);
    expect(document.head.querySelector("script")).toBeNull();
  });
});
