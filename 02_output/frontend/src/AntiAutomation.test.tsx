import { act, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AntiAutomation } from "./AntiAutomation";

afterEach(() => {
  delete window.grecaptcha;
  document.head.querySelectorAll("script").forEach((s) => s.remove());
});

describe("AntiAutomation live mode", () => {
  it("loads Google's script, renders the widget with the site key and passes tokens on", async () => {
    let callbacks: { callback: (t: string) => void; "expired-callback": () => void } | undefined;
    const grecaptcha = {
      ready: (fn: () => void) => fn(),
      render: vi.fn((_el: HTMLElement, params: { sitekey: string } & typeof callbacks) => {
        callbacks = params;
        return 7;
      }),
      reset: vi.fn(),
    };
    const onToken = vi.fn();
    const { rerender } = render(
      <AntiAutomation
        settings={{ mode: "live", siteKey: "public-site-key" }}
        token={null}
        onToken={onToken}
        resetKey={0}
      />,
    );

    const script = document.head.querySelector("script");
    expect(script?.getAttribute("src")).toBe(
      "https://www.google.com/recaptcha/api.js?render=explicit",
    );
    window.grecaptcha = grecaptcha;
    await act(async () => script!.dispatchEvent(new Event("load")));

    expect(grecaptcha.render).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ sitekey: "public-site-key" }),
    );
    callbacks!.callback("live-token");
    expect(onToken).toHaveBeenLastCalledWith("live-token");
    callbacks!["expired-callback"]();
    expect(onToken).toHaveBeenLastCalledWith(null);

    rerender(
      <AntiAutomation
        settings={{ mode: "live", siteKey: "public-site-key" }}
        token={null}
        onToken={onToken}
        resetKey={1}
      />,
    );
    expect(grecaptcha.reset).toHaveBeenCalledWith(7);
  });
});
