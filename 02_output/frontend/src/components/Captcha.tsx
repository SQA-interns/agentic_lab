import { useEffect, useRef } from "react";
import { messages } from "../messages";

interface Grecaptcha {
  render: (
    container: HTMLElement,
    parameters: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
    },
  ) => number;
  reset: (widgetId?: number) => void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoad?: () => void;
  }
}

const TEST_TOKEN = "test-valid";
const SCRIPT_URL = "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoad&render=explicit";

interface CaptchaProps {
  mode: "recaptcha" | "test";
  siteKey: string | null;
  token: string;
  /** Changes after every submission that reached the server: a reCAPTCHA token is single-use. */
  resetSignal: number;
  invalid: boolean;
  onToken: (token: string) => void;
}

/** Anti-automation proof (SR-01): a test checkbox in test mode, otherwise Google reCAPTCHA v2. */
export function Captcha({ mode, siteKey, token, resetSignal, invalid, onToken }: CaptchaProps) {
  if (mode === "test") {
    return (
      <label className="choice">
        <input
          type="checkbox"
          data-testid="captcha-test"
          checked={token === TEST_TOKEN}
          aria-describedby={invalid ? "captchaToken-error" : undefined}
          onChange={(event) => onToken(event.target.checked ? TEST_TOKEN : "")}
        />
        {messages.captchaTest}
      </label>
    );
  }
  return <RecaptchaWidget siteKey={siteKey ?? ""} resetSignal={resetSignal} onToken={onToken} />;
}

function RecaptchaWidget({
  siteKey,
  resetSignal,
  onToken,
}: {
  siteKey: string;
  resetSignal: number;
  onToken: (token: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);
  const onTokenRef = useRef(onToken);
  onTokenRef.current = onToken;

  useEffect(() => {
    const render = () => {
      if (window.grecaptcha && container.current && widgetId.current === null) {
        widgetId.current = window.grecaptcha.render(container.current, {
          sitekey: siteKey,
          callback: (value) => onTokenRef.current(value),
          "expired-callback": () => onTokenRef.current(""),
        });
      }
    };
    if (window.grecaptcha) {
      render();
      return;
    }
    window.onRecaptchaLoad = render;
    if (!document.querySelector(`script[src="${SCRIPT_URL}"]`)) {
      const script = document.createElement("script");
      script.src = SCRIPT_URL;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  }, [siteKey]);

  useEffect(() => {
    if (resetSignal > 0 && window.grecaptcha && widgetId.current !== null) {
      window.grecaptcha.reset(widgetId.current);
      onTokenRef.current("");
    }
  }, [resetSignal]);

  return <div data-testid="captcha-widget" ref={container} />;
}
