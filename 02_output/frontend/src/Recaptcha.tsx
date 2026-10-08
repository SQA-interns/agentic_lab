// Anti-automation widget (SR-01). Test mode shows a plain checkbox and never contacts Google (SR-02).
import { useEffect, useRef } from "react";

export const TEST_MODE_TOKEN = "test-mode-pass";

interface Grecaptcha {
  render: (
    element: HTMLElement,
    options: {
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

const SCRIPT_URL = "https://www.google.com/recaptcha/api.js?render=explicit&onload=onRecaptchaLoad";

interface Props {
  siteKey: string;
  testMode: boolean;
  token: string;
  resetKey: number;
  onToken: (token: string) => void;
}

export function Recaptcha({ siteKey, testMode, token, resetKey, onToken }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<number | null>(null);

  useEffect(() => {
    if (testMode || !container.current) {
      return;
    }
    const element = container.current;
    const render = () => {
      if (window.grecaptcha && widget.current === null) {
        widget.current = window.grecaptcha.render(element, {
          sitekey: siteKey,
          callback: onToken,
          "expired-callback": () => onToken(""),
        });
      }
    };
    if (window.grecaptcha) {
      render();
    } else {
      window.onRecaptchaLoad = render;
      if (!document.querySelector(`script[src="${SCRIPT_URL}"]`)) {
        const script = document.createElement("script");
        script.src = SCRIPT_URL;
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
      }
    }
  }, [siteKey, testMode, onToken]);

  useEffect(() => {
    if (resetKey > 0 && !testMode && window.grecaptcha && widget.current !== null) {
      window.grecaptcha.reset(widget.current);
    }
  }, [resetKey, testMode]);

  if (testMode) {
    return (
      <div data-testid="recaptcha" className="recaptcha">
        <label>
          <input
            type="checkbox"
            data-testid="recaptcha-test"
            checked={token === TEST_MODE_TOKEN}
            onChange={(e) => onToken(e.target.checked ? TEST_MODE_TOKEN : "")}
          />{" "}
          I am not a robot (test mode)
        </label>
      </div>
    );
  }
  return <div data-testid="recaptcha" className="recaptcha" ref={container} />;
}
