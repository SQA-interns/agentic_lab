import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Captcha, TEST_TOKEN } from "./Captcha";

afterEach(() => {
  delete window.grecaptcha;
  document.getElementById("recaptcha-script")?.remove();
});

describe("Captcha", () => {
  it("in test mode yields the deterministic token and loads no script", () => {
    const onToken = vi.fn();
    render(
      <Captcha
        testMode
        siteKey=""
        token=""
        onToken={onToken}
        resetCounter={0}
      />,
    );

    fireEvent.click(screen.getByRole("checkbox", { name: "I am not a robot" }));

    expect(onToken).toHaveBeenCalledWith(TEST_TOKEN);
    expect(document.getElementById("recaptcha-script")).toBeNull();
  });

  it("in production mode renders the Google widget with the site key", async () => {
    const render_ = vi.fn(() => 7);
    const reset = vi.fn();
    window.grecaptcha = { render: render_, reset };
    const onToken = vi.fn();

    const { rerender } = render(
      <Captcha
        testMode={false}
        siteKey="site-key"
        token=""
        onToken={onToken}
        resetCounter={0}
      />,
    );
    await vi.waitFor(() => expect(render_).toHaveBeenCalledTimes(1));
    const params = (
      render_.mock.calls[0] as unknown as [HTMLElement, Record<string, unknown>]
    )[1];
    expect(params.sitekey).toBe("site-key");
    (params.callback as (t: string) => void)("google-token");
    expect(onToken).toHaveBeenCalledWith("google-token");
    (params["expired-callback"] as () => void)();
    expect(onToken).toHaveBeenLastCalledWith("");

    rerender(
      <Captcha
        testMode={false}
        siteKey="site-key"
        token=""
        onToken={onToken}
        resetCounter={1}
      />,
    );
    expect(reset).toHaveBeenCalledWith(7);
  });

  it("loads the reCAPTCHA script when it is not present yet", () => {
    render(
      <Captcha
        testMode={false}
        siteKey="k"
        token=""
        onToken={vi.fn()}
        resetCounter={0}
      />,
    );

    const script = document.getElementById(
      "recaptcha-script",
    ) as HTMLScriptElement;
    expect(script.src).toContain("https://www.google.com/recaptcha/api.js");
    expect(script.src).toContain("render=explicit");
  });

  it("shows its error message", () => {
    render(
      <Captcha
        testMode
        siteKey=""
        token=""
        onToken={vi.fn()}
        resetCounter={0}
        error="Please confirm you are not a robot."
      />,
    );

    expect(
      screen.getByRole("checkbox", { name: "I am not a robot" }),
    ).toHaveAttribute("aria-invalid", "true");
    expect(
      screen.getByText("Please confirm you are not a robot."),
    ).toBeInTheDocument();
  });
});
