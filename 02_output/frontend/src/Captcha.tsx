import { useEffect, useRef } from "react";

// Anti-automation check (SR-01). TEST mode renders a deterministic checkbox (no call to Google);
// GOOGLE mode renders the reCAPTCHA v2 widget with the site key served by the backend (AR-07).

interface GreCaptcha {
  render(
    element: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ): number;
  reset(widget: number): void;
}

declare global {
  interface Window {
    grecaptcha?: GreCaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

const SCRIPT =
  "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit";
let loading: Promise<GreCaptcha> | null = null;

function loadGoogle(): Promise<GreCaptcha> {
  if (!loading) {
    loading = new Promise((resolve) => {
      window.onRecaptchaLoaded = () => resolve(window.grecaptcha as GreCaptcha);
      const script = document.createElement("script");
      script.src = SCRIPT;
      script.async = true;
      document.head.appendChild(script);
    });
  }
  return loading;
}

export const TEST_TOKEN = "test-pass";

interface Props {
  mode: "GOOGLE" | "TEST";
  siteKey?: string;
  token: string;
  resetKey: number;
  error?: string;
  onToken: (token: string) => void;
}

export function Captcha({
  mode,
  siteKey,
  token,
  resetKey,
  error,
  onToken,
}: Props) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<number | null>(null);

  useEffect(() => {
    if (mode !== "GOOGLE" || !siteKey || !container.current) return;
    let cancelled = false;
    void loadGoogle().then((grecaptcha) => {
      if (cancelled || !container.current || widget.current !== null) return;
      widget.current = grecaptcha.render(container.current, {
        sitekey: siteKey,
        callback: onToken,
        "expired-callback": () => onToken(""),
      });
    });
    return () => {
      cancelled = true;
    };
  }, [mode, siteKey, onToken]);

  useEffect(() => {
    if (resetKey > 0 && widget.current !== null && window.grecaptcha) {
      window.grecaptcha.reset(widget.current);
    }
  }, [resetKey]);

  return (
    <div className="captcha" data-testid="captcha">
      {mode === "TEST" ? (
        <label className="check">
          <input
            type="checkbox"
            data-testid="captcha-test-checkbox"
            checked={token === TEST_TOKEN}
            aria-describedby={error ? "error-recaptchaToken" : undefined}
            onChange={(e) => onToken(e.target.checked ? TEST_TOKEN : "")}
          />
          I am not a robot (test mode)
        </label>
      ) : (
        <div ref={container} />
      )}
      {error && (
        <p
          className="error"
          id="error-recaptchaToken"
          data-testid="error-recaptchaToken"
        >
          {error}
        </p>
      )}
    </div>
  );
}
