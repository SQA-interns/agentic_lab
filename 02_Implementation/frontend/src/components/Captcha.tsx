import { useEffect, useRef } from "react";

// reCAPTCHA v2 (specification §8.2). In test mode a local checkbox yields the
// deterministic token "test-pass" and no Google script is loaded.

interface Grecaptcha {
  render(
    container: HTMLElement,
    params: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ): number;
  reset(widgetId?: number): void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

const SCRIPT_ID = "recaptcha-script";
export const TEST_TOKEN = "test-pass";

function loadRecaptcha(): Promise<Grecaptcha> {
  return new Promise((resolve) => {
    if (window.grecaptcha) {
      resolve(window.grecaptcha);
      return;
    }
    window.onRecaptchaLoaded = () => resolve(window.grecaptcha as Grecaptcha);
    if (!document.getElementById(SCRIPT_ID)) {
      const script = document.createElement("script");
      script.id = SCRIPT_ID;
      script.src =
        "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit";
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });
}

interface CaptchaProps {
  testMode: boolean;
  siteKey: string;
  token: string;
  onToken: (token: string) => void;
  resetCounter: number;
  error?: string;
}

export function Captcha({
  testMode,
  siteKey,
  token,
  onToken,
  resetCounter,
  error,
}: CaptchaProps) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);

  useEffect(() => {
    if (testMode || !siteKey || !container.current) return;
    let cancelled = false;
    loadRecaptcha().then((grecaptcha) => {
      if (cancelled || !container.current || widgetId.current !== null) return;
      widgetId.current = grecaptcha.render(container.current, {
        sitekey: siteKey,
        callback: onToken,
        "expired-callback": () => onToken(""),
      });
    });
    return () => {
      cancelled = true;
    };
  }, [testMode, siteKey, onToken]);

  useEffect(() => {
    if (resetCounter === 0) return;
    if (!testMode && window.grecaptcha && widgetId.current !== null) {
      window.grecaptcha.reset(widgetId.current);
    }
  }, [resetCounter, testMode]);

  const errorId = "field-recaptchaToken-error";
  return (
    <div className="field">
      {testMode ? (
        <label className="checkbox">
          <input
            type="checkbox"
            checked={token === TEST_TOKEN}
            onChange={(e) => onToken(e.target.checked ? TEST_TOKEN : "")}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
          />
          I am not a robot
        </label>
      ) : (
        <div ref={container} aria-describedby={error ? errorId : undefined} />
      )}
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
