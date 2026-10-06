// Anti-automation check (SR-01): the Google reCAPTCHA v2 widget, or the deterministic test mode.
import { useEffect, useRef } from "react";

interface Grecaptcha {
  render(
    container: HTMLElement,
    parameters: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ): number;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

const SCRIPT_ID = "recaptcha-script";
const TEST_TOKEN = "test-pass";

interface Props {
  mode: "live" | "test";
  siteKey?: string;
  token: string;
  onToken: (token: string) => void;
  error?: string;
}

export function Captcha({ mode, siteKey, token, onToken, error }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const errorId = "captchaToken-error";

  useEffect(() => {
    if (mode !== "live" || !siteKey || !container.current) {
      return;
    }
    const target = container.current;
    const render = () => {
      if (window.grecaptcha && target.childElementCount === 0) {
        window.grecaptcha.render(target, {
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
  }, [mode, siteKey, onToken]);

  return (
    <div className="captcha">
      {mode === "test" ? (
        <label>
          <input
            type="checkbox"
            checked={token === TEST_TOKEN}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(e) => onToken(e.target.checked ? TEST_TOKEN : "")}
          />{" "}
          I am not a robot (test mode)
        </label>
      ) : (
        <div ref={container} aria-describedby={error ? errorId : undefined} />
      )}
      {error && (
        <p id={errorId} role="alert" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
