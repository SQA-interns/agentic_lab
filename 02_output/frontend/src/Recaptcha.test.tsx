import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Recaptcha, TEST_MODE_TOKEN } from "./Recaptcha";

afterEach(() => {
  cleanup();
  document.head.querySelectorAll("script").forEach((s) => s.remove());
  delete window.grecaptcha;
  delete window.onRecaptchaLoad;
});

describe("Recaptcha", () => {
  it("test mode toggles the fixed token and loads nothing from Google (SR-02)", () => {
    const onToken = vi.fn();
    render(<Recaptcha siteKey="" testMode token="" resetKey={0} onToken={onToken} />);
    fireEvent.click(screen.getByTestId("recaptcha-test"));
    expect(onToken).toHaveBeenCalledWith(TEST_MODE_TOKEN);
    expect(document.querySelector("script")).toBeNull();
  });

  it("production mode loads the Google script once and renders with the site key", () => {
    const onToken = vi.fn();
    const { unmount } = render(
      <Recaptcha siteKey="site-key" testMode={false} token="" resetKey={0} onToken={onToken} />,
    );
    const scripts = document.head.querySelectorAll(
      'script[src^="https://www.google.com/recaptcha/api.js"]',
    );
    expect(scripts).toHaveLength(1);
    expect(screen.queryByTestId("recaptcha-test")).toBeNull();

    const renderWidget = vi.fn(
      (
        _el: HTMLElement,
        options: { sitekey: string; callback: (t: string) => void; "expired-callback": () => void },
      ) => {
        options.callback("google-token");
        options["expired-callback"]();
        return 7;
      },
    );
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    window.onRecaptchaLoad?.();
    expect(renderWidget.mock.calls[0][1].sitekey).toBe("site-key");
    expect(onToken).toHaveBeenNthCalledWith(1, "google-token");
    expect(onToken).toHaveBeenNthCalledWith(2, "");
    unmount();
  });

  it("resets the widget when asked", () => {
    const reset = vi.fn();
    window.grecaptcha = { render: vi.fn(() => 3), reset };
    const { rerender } = render(
      <Recaptcha siteKey="k" testMode={false} token="" resetKey={0} onToken={vi.fn()} />,
    );
    rerender(<Recaptcha siteKey="k" testMode={false} token="" resetKey={1} onToken={vi.fn()} />);
    expect(reset).toHaveBeenCalledWith(3);
  });
});
