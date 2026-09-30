import { useEffect, useRef } from "react";

// Google reCAPTCHA v2 checkbox, or the deterministic test mode (docs/02_contracts/recaptcha.md).

export const TEST_MODE_TOKEN = "test-mode-pass";
const SCRIPT_ID = "recaptcha-script";

type Grecaptcha = {
  render: (
    element: HTMLElement,
    options: { sitekey: string; callback: (token: string) => void; "expired-callback": () => void },
  ) => number;
  reset: (widgetId?: number) => void;
};

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

type Props = {
  siteKey: string;
  testMode: boolean;
  resetCount: number;
  onToken: (token: string) => void;
  error?: string;
};

export function Recaptcha({ siteKey, testMode, resetCount, onToken, error }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);

  useEffect(() => {
    if (testMode) {
      onToken(TEST_MODE_TOKEN);
      return;
    }
    const render = () => {
      if (container.current && window.grecaptcha && widgetId.current === null) {
        widgetId.current = window.grecaptcha.render(container.current, {
          sitekey: siteKey,
          callback: onToken,
          "expired-callback": () => onToken(""),
        });
      }
    };
    if (window.grecaptcha) {
      render();
    } else if (!document.getElementById(SCRIPT_ID)) {
      window.onRecaptchaLoaded = render;
      const script = document.createElement("script");
      script.id = SCRIPT_ID;
      script.src =
        "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit";
      script.async = true;
      document.head.appendChild(script);
    }
  }, [siteKey, testMode, onToken]);

  useEffect(() => {
    if (resetCount > 0 && !testMode && window.grecaptcha && widgetId.current !== null) {
      window.grecaptcha.reset(widgetId.current);
      onToken("");
    }
  }, [resetCount, testMode, onToken]);

  return (
    <div className="recaptcha" aria-describedby={error ? "recaptchaToken-error" : undefined}>
      {testMode ? <p>reCAPTCHA test mode</p> : <div ref={container} />}
      {error && (
        <p id="recaptchaToken-error" className="error">
          {error}
        </p>
      )}
    </div>
  );
}
