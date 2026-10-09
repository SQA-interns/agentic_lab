import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Captcha, TEST_TOKEN } from "./Captcha";

describe("Captcha (SR-01, SR-02)", () => {
  it("test mode yields the deterministic token and clears it again", () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha mode="TEST" token="" resetKey={0} onToken={onToken} />,
    );

    fireEvent.click(screen.getByTestId("captcha-test-checkbox"));
    expect(onToken).toHaveBeenLastCalledWith(TEST_TOKEN);

    rerender(
      <Captcha mode="TEST" token={TEST_TOKEN} resetKey={0} onToken={onToken} />,
    );
    fireEvent.click(screen.getByTestId("captcha-test-checkbox"));
    expect(onToken).toHaveBeenLastCalledWith("");
  });

  it("Google mode loads the widget script once and renders no test checkbox", () => {
    render(
      <Captcha
        mode="GOOGLE"
        siteKey="site"
        token=""
        resetKey={0}
        onToken={vi.fn()}
      />,
    );

    expect(
      screen.queryByTestId("captcha-test-checkbox"),
    ).not.toBeInTheDocument();
    const scripts = document.head.querySelectorAll(
      'script[src^="https://www.google.com/recaptcha/"]',
    );
    expect(scripts).toHaveLength(1);
  });

  it("shows the error with its id for aria-describedby", () => {
    render(
      <Captcha
        mode="TEST"
        token=""
        resetKey={0}
        error="Please confirm"
        onToken={vi.fn()}
      />,
    );

    expect(screen.getByTestId("error-recaptchaToken")).toHaveAttribute(
      "id",
      "error-recaptchaToken",
    );
    expect(screen.getByTestId("captcha-test-checkbox")).toHaveAttribute(
      "aria-describedby",
      "error-recaptchaToken",
    );
  });
});
