import { useEffect, useRef } from 'react';

export const STUB_TOKEN = 'local-captcha-ok';
export const STUB_LABEL = 'Local test captcha: I am not a robot';

interface Props {
  mode: 'stub' | 'recaptcha';
  siteKey: string | null;
  token: string;
  error?: string;
  onToken: (token: string) => void;
}

interface GreCaptcha {
  render: (el: HTMLElement, options: Record<string, unknown>) => number;
}

declare global {
  interface Window {
    grecaptcha?: GreCaptcha;
    onRecaptchaLoad?: () => void;
  }
}

/** Stub checkbox for local/test, Google reCAPTCHA v2 widget in production (SR-01). */
export function Captcha({ mode, siteKey, token, error, onToken }: Props) {
  const widget = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (mode !== 'recaptcha' || !siteKey || !widget.current) return;
    const target = widget.current;
    const render = () => {
      window.grecaptcha?.render(target, {
        sitekey: siteKey,
        callback: (t: string) => {
          onToken(t);
        },
        'expired-callback': () => {
          onToken('');
        },
      });
    };
    if (window.grecaptcha) {
      render();
      return;
    }
    window.onRecaptchaLoad = render;
    const script = document.createElement('script');
    script.src = 'https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoad&render=explicit';
    script.async = true;
    document.head.appendChild(script);
  }, [mode, siteKey, onToken]);

  const errorId = error ? 'captchaToken-error' : undefined;
  if (mode === 'recaptcha') {
    return (
      <div className="field">
        <div ref={widget} aria-describedby={errorId} />
        {error && (
          <p id="captchaToken-error" className="error">
            {error}
          </p>
        )}
      </div>
    );
  }
  return (
    <div className="field checkbox">
      <input
        id="captcha-stub"
        type="checkbox"
        checked={token === STUB_TOKEN}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        onChange={(e) => {
          onToken(e.target.checked ? STUB_TOKEN : '');
        }}
      />
      <label htmlFor="captcha-stub">{STUB_LABEL}</label>
      {error && (
        <p id="captchaToken-error" className="error">
          {error}
        </p>
      )}
    </div>
  );
}
