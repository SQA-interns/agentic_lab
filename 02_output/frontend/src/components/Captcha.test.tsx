import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Captcha } from "./Captcha";

afterEach(() => {
  cleanup();
  delete window.grecaptcha;
  delete window.onRecaptchaLoad;
  document.head.querySelectorAll("script").forEach((script) => script.remove());
});

describe("captcha", () => {
  it("gives the test token while the test checkbox is checked", () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha
        mode="test"
        siteKey={null}
        token=""
        resetSignal={0}
        invalid={false}
        onToken={onToken}
      />,
    );

    fireEvent.click(screen.getByTestId("captcha-test"));
    expect(onToken).toHaveBeenLastCalledWith("test-valid");

    rerender(
      <Captcha
        mode="test"
        siteKey={null}
        token="test-valid"
        resetSignal={0}
        invalid={false}
        onToken={onToken}
      />,
    );
    expect((screen.getByTestId("captcha-test") as HTMLInputElement).checked).toBe(true);
    fireEvent.click(screen.getByTestId("captcha-test"));
    expect(onToken).toHaveBeenLastCalledWith("");
  });

  it("loads Google's script once and renders the widget with the site key", () => {
    const onToken = vi.fn();
    render(
      <Captcha
        mode="recaptcha"
        siteKey="site-1"
        token=""
        resetSignal={0}
        invalid={false}
        onToken={onToken}
      />,
    );

    const scripts = document.head.querySelectorAll("script");
    expect(scripts).toHaveLength(1);
    expect(scripts[0]!.getAttribute("src")).toBe(
      "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoad&render=explicit",
    );
    expect(screen.queryByTestId("captcha-test")).toBeNull();

    const render_ = vi.fn(
      (
        _container: HTMLElement,
        parameters: {
          sitekey: string;
          callback: (t: string) => void;
          "expired-callback": () => void;
        },
      ) => {
        parameters.callback("google-token");
        parameters["expired-callback"]();
        return 7;
      },
    );
    window.grecaptcha = { render: render_, reset: vi.fn() };
    window.onRecaptchaLoad!();

    expect(render_).toHaveBeenCalledWith(screen.getByTestId("captcha-widget"), expect.anything());
    expect(render_.mock.calls[0]![1].sitekey).toBe("site-1");
    expect(onToken.mock.calls).toEqual([["google-token"], [""]]);
  });

  it("renders at once when the script is already loaded and resets after a submission", () => {
    const reset = vi.fn();
    window.grecaptcha = { render: vi.fn(() => 3), reset };
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha
        mode="recaptcha"
        siteKey="k"
        token=""
        resetSignal={0}
        invalid={false}
        onToken={onToken}
      />,
    );
    expect(window.grecaptcha.render).toHaveBeenCalledTimes(1);
    expect(document.head.querySelectorAll("script")).toHaveLength(0);

    rerender(
      <Captcha
        mode="recaptcha"
        siteKey="k"
        token="x"
        resetSignal={1}
        invalid={false}
        onToken={onToken}
      />,
    );

    expect(reset).toHaveBeenCalledWith(3);
    expect(onToken).toHaveBeenLastCalledWith("");
  });
});
