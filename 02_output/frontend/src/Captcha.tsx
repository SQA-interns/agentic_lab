import { useEffect, useRef } from 'react';

// Anti-automation widget (SR-01): Google reCAPTCHA v2 with the public site key from the backend
// (AR-07), or in test mode a local checkbox that yields the deterministic test token (SR-02).

export const TEST_MODE_TOKEN = 'test-mode-token';

interface GreCaptcha {
  render(
    element: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      'expired-callback': () => void;
    },
  ): number;
  reset(widgetId?: number): void;
}

declare global {
  interface Window {
    grecaptcha?: GreCaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

interface Props {
  testMode: boolean;
  siteKey: string;
  token: string;
  resetCount: number;
  error?: string;
  onToken: (token: string) => void;
}

const SCRIPT_ID = 'recaptcha-script';

export function Captcha({ testMode, siteKey, token, resetCount, error, onToken }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<number | null>(null);

  useEffect(() => {
    if (testMode || !siteKey) return;
    const render = () => {
      if (!container.current || !window.grecaptcha || widget.current !== null) return;
      widget.current = window.grecaptcha.render(container.current, {
        sitekey: siteKey,
        callback: onToken,
        'expired-callback': () => onToken(''),
      });
    };
    if (window.grecaptcha) {
      render();
    } else if (!document.getElementById(SCRIPT_ID)) {
      window.onRecaptchaLoaded = render;
      const script = document.createElement('script');
      script.id = SCRIPT_ID;
      script.src =
        'https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit';
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  }, [testMode, siteKey, onToken]);

  useEffect(() => {
    if (resetCount > 0 && widget.current !== null && window.grecaptcha) {
      window.grecaptcha.reset(widget.current);
    }
  }, [resetCount]);

  const errorId = 'captchaToken-error';
  return (
    <div className="field">
      {testMode ? (
        <label className="checkbox">
          <input
            type="checkbox"
            checked={token === TEST_MODE_TOKEN}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(e) => onToken(e.target.checked ? TEST_MODE_TOKEN : '')}
          />
          I am not a robot (test mode)
        </label>
      ) : (
        <div ref={container} aria-describedby={error ? errorId : undefined} />
      )}
      {error && (
        <p className="error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}
