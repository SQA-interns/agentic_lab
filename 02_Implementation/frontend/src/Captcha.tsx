import { useEffect, useRef } from 'react';
import type { CaptchaConfig } from './types';

interface Grecaptcha {
  render: (
    el: HTMLElement,
    opts: { sitekey: string; callback: (t: string) => void; 'expired-callback': () => void },
  ) => number;
  reset: (id?: number) => void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoad?: () => void;
  }
}

interface Props {
  config: CaptchaConfig;
  token: string | null;
  resetKey: number;
  onToken: (token: string | null) => void;
}

/**
 * reCAPTCHA v2 widget in production mode; in the explicit non-production test mode a
 * deterministic checkbox supplies the backend-configured test token (ST-05).
 */
export function Captcha({ config, token, resetKey, onToken }: Props) {
  if (config.mode === 'test') {
    return (
      <div className="captcha">
        <label className="checkbox">
          <input
            type="checkbox"
            checked={token !== null}
            onChange={(e) => onToken(e.target.checked ? (config.testToken ?? '') : null)}
          />
          I am not a robot (local test captcha)
        </label>
      </div>
    );
  }
  return <Recaptcha siteKey={config.siteKey ?? ''} resetKey={resetKey} onToken={onToken} />;
}

function Recaptcha({
  siteKey,
  resetKey,
  onToken,
}: {
  siteKey: string;
  resetKey: number;
  onToken: (token: string | null) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);
  const tokenCallback = useRef(onToken);

  useEffect(() => {
    tokenCallback.current = onToken;
  }, [onToken]);

  useEffect(() => {
    const render = () => {
      if (window.grecaptcha && container.current && widgetId.current === null) {
        widgetId.current = window.grecaptcha.render(container.current, {
          sitekey: siteKey,
          callback: (t) => tokenCallback.current(t),
          'expired-callback': () => tokenCallback.current(null),
        });
      }
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
  }, [siteKey]);

  useEffect(() => {
    if (resetKey > 0 && window.grecaptcha && widgetId.current !== null) {
      window.grecaptcha.reset(widgetId.current);
    }
  }, [resetKey]);

  return <div className="captcha" ref={container} />;
}
