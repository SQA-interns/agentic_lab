import { useEffect, useRef } from 'react';
import { CAPTCHA_TEST_LABEL, CAPTCHA_TEST_TOKEN } from './contract';

// Anti-automation widget (SR-01): the reCAPTCHA v2 checkbox with the site key from the form
// configuration, or a plain checkbox in the deterministic test mode (SR-02).

interface GreCaptcha {
  render(
    container: HTMLElement,
    parameters: {
      sitekey: string;
      callback: (token: string) => void;
      'expired-callback': () => void;
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

const SCRIPT_ID = 'recaptcha-script';
const SCRIPT_URL =
  'https://www.google.com/recaptcha/api.js?render=explicit&onload=onRecaptchaLoaded';

export interface CaptchaProps {
  testMode: boolean;
  siteKey: string | null;
  token: string | null;
  resetCount: number;
  errorId?: string;
  onToken: (token: string | null) => void;
}

export function Captcha({ testMode, siteKey, token, resetCount, errorId, onToken }: CaptchaProps) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<number | null>(null);
  const callback = useRef(onToken);
  callback.current = onToken;

  useEffect(() => {
    if (testMode || !siteKey || !container.current) {
      return;
    }
    const element = container.current;
    const render = () => {
      if (window.grecaptcha && widget.current === null && element.childElementCount === 0) {
        widget.current = window.grecaptcha.render(element, {
          sitekey: siteKey,
          callback: (value) => callback.current(value),
          'expired-callback': () => callback.current(null),
        });
      }
    };
    if (window.grecaptcha) {
      render();
    } else {
      window.onRecaptchaLoaded = render;
      if (!document.getElementById(SCRIPT_ID)) {
        const script = document.createElement('script');
        script.id = SCRIPT_ID;
        script.src = SCRIPT_URL;
        script.async = true;
        script.defer = true;
        document.head.appendChild(script);
      }
    }
  }, [testMode, siteKey]);

  useEffect(() => {
    if (resetCount > 0 && widget.current !== null && window.grecaptcha) {
      window.grecaptcha.reset(widget.current);
    }
  }, [resetCount]);

  if (testMode) {
    return (
      <div className="captcha">
        <input
          id="captcha-test"
          type="checkbox"
          checked={token === CAPTCHA_TEST_TOKEN}
          aria-invalid={errorId ? true : undefined}
          aria-describedby={errorId}
          onChange={(e) => onToken(e.target.checked ? CAPTCHA_TEST_TOKEN : null)}
        />
        <label htmlFor="captcha-test">{CAPTCHA_TEST_LABEL}</label>
      </div>
    );
  }
  return <div className="captcha" ref={container} aria-describedby={errorId} />;
}
