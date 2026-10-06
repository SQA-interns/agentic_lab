import { useEffect, useRef } from "react";
import { LABELS } from "../messages";

/** Token the backend's test mode accepts (recaptcha.schema.json). */
export const TEST_PASS_TOKEN = "test-pass";

interface GoogleRecaptcha {
  render: (
    element: HTMLElement,
    params: { sitekey: string; callback: (token: string) => void; "expired-callback": () => void },
  ) => number;
}

declare global {
  interface Window {
    grecaptcha?: GoogleRecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

interface Props {
  testMode: boolean;
  siteKey: string;
  token: string;
  onToken: (token: string) => void;
}

/** reCAPTCHA v2 checkbox, or a labelled test checkbox when the backend runs in test mode. */
export function Captcha({ testMode, siteKey, token, onToken }: Props) {
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (testMode || !container.current) return;
    const element = container.current;
    const render = () =>
      window.grecaptcha?.render(element, {
        sitekey: siteKey,
        callback: onToken,
        "expired-callback": () => onToken(""),
      });
    if (window.grecaptcha) {
      render();
      return;
    }
    window.onRecaptchaLoaded = render;
    const script = document.createElement("script");
    script.src = "https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit";
    script.async = true;
    document.head.appendChild(script);
  }, [testMode, siteKey, onToken]);

  if (testMode) {
    return (
      <div className="check">
        <input
          type="checkbox"
          id="captcha-test"
          data-testid="recaptcha"
          checked={token === TEST_PASS_TOKEN}
          onChange={(e) => onToken(e.target.checked ? TEST_PASS_TOKEN : "")}
        />
        <label htmlFor="captcha-test">{LABELS.testCaptcha}</label>
      </div>
    );
  }
  return <div ref={container} data-testid="recaptcha" />;
}
