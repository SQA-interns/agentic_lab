import { useEffect, useRef } from "react";
import type { FormConfig } from "./api";
import { texts } from "./texts";

export const TEST_MODE_TOKEN = "test-mode-pass";
const SCRIPT_URL = "https://www.google.com/recaptcha/api.js?render=explicit";

interface Grecaptcha {
  ready(callback: () => void): void;
  render(
    container: HTMLElement,
    parameters: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ): number;
  reset(widgetId: number): void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
  }
}

let scriptPromise: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = SCRIPT_URL;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("reCAPTCHA could not be loaded"));
      document.head.appendChild(script);
    });
  }
  return scriptPromise;
}

interface Props {
  settings: FormConfig["antiAutomation"];
  token: string | null;
  onToken: (token: string | null) => void;
  /** Changes whenever the widget must be reset (after a rejected token). */
  resetKey: number;
  error?: string;
}

/** SR-01: the Google reCAPTCHA v2 widget, or the deterministic test-mode checkbox (SR-02). */
export function AntiAutomation({ settings, token, onToken, resetKey, error }: Props) {
  const errorId = error ? "antiAutomation-error" : undefined;
  return (
    <div className="field">
      {settings.mode === "test" ? (
        <label className="check">
          <input
            type="checkbox"
            checked={token === TEST_MODE_TOKEN}
            onChange={(e) => onToken(e.target.checked ? TEST_MODE_TOKEN : null)}
            aria-invalid={error ? true : undefined}
            aria-describedby={errorId}
          />{" "}
          {texts.robotTestMode}
        </label>
      ) : (
        <RecaptchaWidget siteKey={settings.siteKey ?? ""} onToken={onToken} resetKey={resetKey} />
      )}
      {error && (
        <p id={errorId} className="error">
          {error}
        </p>
      )}
    </div>
  );
}

function RecaptchaWidget({
  siteKey,
  onToken,
  resetKey,
}: {
  siteKey: string;
  onToken: (token: string | null) => void;
  resetKey: number;
}) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);
  const callback = useRef(onToken);
  callback.current = onToken;

  useEffect(() => {
    let cancelled = false;
    loadScript()
      .then(() => {
        window.grecaptcha?.ready(() => {
          if (!cancelled && container.current && widgetId.current === null && window.grecaptcha) {
            widgetId.current = window.grecaptcha.render(container.current, {
              sitekey: siteKey,
              callback: (t) => callback.current(t),
              "expired-callback": () => callback.current(null),
            });
          }
        });
      })
      .catch(() => callback.current(null));
    return () => {
      cancelled = true;
    };
  }, [siteKey]);

  useEffect(() => {
    if (widgetId.current !== null && window.grecaptcha) {
      window.grecaptcha.reset(widgetId.current);
    }
  }, [resetKey]);

  return <div ref={container} />;
}
