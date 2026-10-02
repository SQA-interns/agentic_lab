// Unit tests of the anti-automation control in both modes (SR-01, SR-02, AR-07).
import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Captcha } from "./Captcha";

afterEach(() => {
  cleanup();
  delete window.grecaptcha;
  document.head.querySelectorAll("script").forEach((script) => script.remove());
});

describe("Captcha in test mode", () => {
  it("yields the passing token when ticked and none when cleared, without loading any script", () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha
        captcha={{ mode: "test", siteKey: "" }}
        token=""
        onToken={onToken}
        resetSignal={0}
        errorId={undefined}
      />,
    );
    const box = screen.getByRole("checkbox", { name: "Nisem robot (testni način)" });
    expect(box).not.toBeChecked();

    fireEvent.click(box);
    expect(onToken).toHaveBeenLastCalledWith("test-pass");

    rerender(
      <Captcha
        captcha={{ mode: "test", siteKey: "" }}
        token="test-pass"
        onToken={onToken}
        resetSignal={0}
        errorId={undefined}
      />,
    );
    expect(box).toBeChecked();
    fireEvent.click(box);
    expect(onToken).toHaveBeenLastCalledWith("");
    expect(document.head.querySelector("script")).toBeNull();
  });

  it("links the control to its error message", () => {
    render(
      <>
        <Captcha
          captcha={{ mode: "test", siteKey: "" }}
          token=""
          onToken={() => undefined}
          resetSignal={0}
          errorId="captcha-error"
        />
        <p id="captcha-error">Potrdite, da niste robot.</p>
      </>,
    );

    const box = screen.getByRole("checkbox");
    expect(box).toBeInvalid();
    expect(box).toHaveAccessibleDescription("Potrdite, da niste robot.");
  });
});

describe("Captcha in reCAPTCHA mode", () => {
  function installRecaptcha() {
    const parameters: {
      sitekey?: string;
      callback?: (token: string) => void;
      expired?: () => void;
    } = {};
    const recaptcha = {
      ready: vi.fn((callback: () => void) => callback()),
      render: vi.fn(
        (
          _container: HTMLElement,
          options: {
            sitekey: string;
            callback: (token: string) => void;
            "expired-callback": () => void;
          },
        ) => {
          parameters.sitekey = options.sitekey;
          parameters.callback = options.callback;
          parameters.expired = options["expired-callback"];
          return 7;
        },
      ),
      reset: vi.fn(),
    };
    window.grecaptcha = recaptcha;
    return { recaptcha, parameters };
  }

  it("AR-07 renders the widget with the site key and passes its token on", () => {
    const { recaptcha, parameters } = installRecaptcha();
    const onToken = vi.fn();

    render(
      <Captcha
        captcha={{ mode: "recaptcha", siteKey: "site-key" }}
        token=""
        onToken={onToken}
        resetSignal={0}
        errorId={undefined}
      />,
    );

    expect(recaptcha.render).toHaveBeenCalledTimes(1);
    expect(parameters.sitekey).toBe("site-key");
    expect(screen.queryByRole("checkbox")).toBeNull();
    parameters.callback?.("token-from-google");
    expect(onToken).toHaveBeenLastCalledWith("token-from-google");
    parameters.expired?.();
    expect(onToken).toHaveBeenLastCalledWith("");
  });

  it("asks for a new check after a token was used", () => {
    const { recaptcha } = installRecaptcha();
    const props = {
      captcha: { mode: "recaptcha" as const, siteKey: "site-key" },
      token: "",
      onToken: () => undefined,
      errorId: undefined,
    };
    const { rerender } = render(<Captcha {...props} resetSignal={0} />);
    expect(recaptcha.reset).not.toHaveBeenCalled();

    rerender(<Captcha {...props} resetSignal={1} />);

    expect(recaptcha.reset).toHaveBeenCalledWith(7);
    expect(recaptcha.render).toHaveBeenCalledTimes(1);
  });

  it("loads the Google script only in this mode and renders once it has loaded", () => {
    render(
      <Captcha
        captcha={{ mode: "recaptcha", siteKey: "site-key" }}
        token=""
        onToken={() => undefined}
        resetSignal={0}
        errorId={undefined}
      />,
    );

    const script = document.head.querySelector("script");
    expect(script?.getAttribute("src")).toBe(
      "https://www.google.com/recaptcha/api.js?render=explicit",
    );

    const { recaptcha } = installRecaptcha();
    script?.dispatchEvent(new Event("load"));
    expect(recaptcha.render).toHaveBeenCalledTimes(1);
  });
});
