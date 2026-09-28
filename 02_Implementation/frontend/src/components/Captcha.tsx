import { useEffect, useRef } from "react";
import type { CaptchaConfig } from "../api";

export const TEST_MODE_TOKEN = "test-mode-pass";

interface Grecaptcha {
  render: (
    container: HTMLElement,
    params: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
      "error-callback": () => void;
    },
  ) => number;
  reset: (widgetId?: number) => void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

const SCRIPT_ID = "recaptcha-script";

function loadRecaptcha(): Promise<Grecaptcha> {
  return new Promise((resolve) => {
    if (window.grecaptcha?.render) {
      resolve(window.grecaptcha);
      return;
    }
    window.onRecaptchaLoaded = () => {
      if (window.grecaptcha) {
        resolve(window.grecaptcha);
      }
    };
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
  config: CaptchaConfig;
  token: string | null;
  onTokenChange: (token: string | null) => void;
  /** Incremented by the parent to reset the widget (e.g. after a rejected token). */
  resetCounter: number;
  error?: string;
}

/**
 * reCAPTCHA v2 checkbox, or a deterministic local checkbox in test mode
 * (local development and automated tests never call Google).
 */
export function Captcha({
  config,
  token,
  onTokenChange,
  resetCounter,
  error,
}: CaptchaProps) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);

  useEffect(() => {
    if (config.mode !== "RECAPTCHA" || !config.siteKey) {
      return;
    }
    let cancelled = false;
    void loadRecaptcha().then((grecaptcha) => {
      if (cancelled || !container.current || widgetId.current !== null) {
        return;
      }
      widgetId.current = grecaptcha.render(container.current, {
        sitekey: config.siteKey as string,
        callback: (value) => onTokenChange(value),
        "expired-callback": () => onTokenChange(null),
        "error-callback": () => onTokenChange(null),
      });
    });
    return () => {
      cancelled = true;
    };
  }, [config.mode, config.siteKey, onTokenChange]);

  useEffect(() => {
    if (resetCounter > 0 && widgetId.current !== null) {
      window.grecaptcha?.reset(widgetId.current);
    }
  }, [resetCounter]);

  return (
    <div className="captcha">
      {config.mode === "TEST" ? (
        <label className="checkbox">
          <input
            type="checkbox"
            checked={token === TEST_MODE_TOKEN}
            onChange={(e) =>
              onTokenChange(e.target.checked ? TEST_MODE_TOKEN : null)
            }
          />
          I am not a robot (test mode)
        </label>
      ) : (
        <div ref={container} data-testid="recaptcha" />
      )}
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
