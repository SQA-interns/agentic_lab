import { useEffect, useRef } from "react";
import type { FormConfig } from "./api";
import { CAPTCHA_TEST_LABEL } from "./texts";

const RECAPTCHA_SCRIPT = "https://www.google.com/recaptcha/api.js?render=explicit";
const TEST_PASS_TOKEN = "test-pass";

interface Recaptcha {
  ready: (callback: () => void) => void;
  render: (
    container: HTMLElement,
    parameters: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ) => number;
  reset: (widgetId: number) => void;
}

declare global {
  interface Window {
    grecaptcha?: Recaptcha;
  }
}

interface CaptchaProps {
  captcha: FormConfig["captcha"];
  token: string;
  onToken: (token: string) => void;
  /** Changes whenever a used token must be replaced by a new check. */
  resetSignal: number;
  errorId: string | undefined;
}

/**
 * The anti-automation control (recaptcha-verify.schema.json): the Google reCAPTCHA v2 checkbox, or
 * in test mode a plain checkbox that yields the passing token without any network call.
 */
export function Captcha({ captcha, token, onToken, resetSignal, errorId }: CaptchaProps) {
  if (captcha.mode === "test") {
    return (
      <label className="choice">
        <input
          type="checkbox"
          checked={token === TEST_PASS_TOKEN}
          aria-describedby={errorId}
          aria-invalid={errorId ? true : undefined}
          onChange={(event) => onToken(event.target.checked ? TEST_PASS_TOKEN : "")}
        />{" "}
        {CAPTCHA_TEST_LABEL}
      </label>
    );
  }
  return (
    <RecaptchaWidget
      siteKey={captcha.siteKey}
      onToken={onToken}
      resetSignal={resetSignal}
      errorId={errorId}
    />
  );
}

interface RecaptchaWidgetProps {
  siteKey: string;
  onToken: (token: string) => void;
  resetSignal: number;
  errorId: string | undefined;
}

function RecaptchaWidget({ siteKey, onToken, resetSignal, errorId }: RecaptchaWidgetProps) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);
  const tokenHandler = useRef(onToken);

  useEffect(() => {
    tokenHandler.current = onToken;
  }, [onToken]);

  useEffect(() => {
    let cancelled = false;
    const render = () => {
      window.grecaptcha?.ready(() => {
        if (cancelled || !container.current || widgetId.current !== null || !window.grecaptcha) {
          return;
        }
        widgetId.current = window.grecaptcha.render(container.current, {
          sitekey: siteKey,
          callback: (token) => tokenHandler.current(token),
          "expired-callback": () => tokenHandler.current(""),
        });
      });
    };
    if (window.grecaptcha) {
      render();
    } else {
      const script = document.createElement("script");
      script.src = RECAPTCHA_SCRIPT;
      script.async = true;
      script.defer = true;
      script.addEventListener("load", render);
      document.head.appendChild(script);
    }
    return () => {
      cancelled = true;
    };
  }, [siteKey]);

  useEffect(() => {
    // A token can be verified only once, so the widget asks again after each use.
    if (resetSignal > 0 && widgetId.current !== null) {
      window.grecaptcha?.reset(widgetId.current);
    }
  }, [resetSignal]);

  return <div ref={container} aria-describedby={errorId} />;
}
