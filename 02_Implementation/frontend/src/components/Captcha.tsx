import { useEffect, useRef, useState } from "react";

export const TEST_MODE_TOKEN = "test-mode-token";

const SCRIPT_URL =
  "https://www.google.com/recaptcha/api.js?render=explicit&onload=onRecaptchaLoaded";

interface Grecaptcha {
  render(
    container: HTMLElement,
    params: {
      sitekey: string;
      callback: (token: string) => void;
      "expired-callback": () => void;
      "error-callback": () => void;
    },
  ): number;
  reset(widgetId?: number): void;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
    onRecaptchaLoaded?: () => void;
  }
}

let scriptPromise: Promise<Grecaptcha> | null = null;

function loadRecaptcha(): Promise<Grecaptcha> {
  if (window.grecaptcha) {
    return Promise.resolve(window.grecaptcha);
  }
  scriptPromise ??= new Promise<Grecaptcha>((resolve, reject) => {
    window.onRecaptchaLoaded = () => {
      if (window.grecaptcha) {
        resolve(window.grecaptcha);
      }
    };
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.defer = true;
    script.onerror = () => {
      scriptPromise = null;
      reject(new Error("reCAPTCHA could not be loaded"));
    };
    document.head.appendChild(script);
  });
  return scriptPromise;
}

interface CaptchaProps {
  siteKey: string;
  testMode: boolean;
  resetSignal: number;
  error?: string;
  onChange: (token: string | null) => void;
}

export function Captcha(props: CaptchaProps) {
  return props.testMode ? (
    <TestModeCaptcha {...props} />
  ) : (
    <RecaptchaWidget {...props} />
  );
}

function TestModeCaptcha({ resetSignal, error, onChange }: CaptchaProps) {
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    setChecked(false);
  }, [resetSignal]);

  return (
    <div className="captcha">
      <label>
        <input
          type="checkbox"
          checked={checked}
          aria-describedby={error ? "captcha-error" : undefined}
          onChange={(event) => {
            setChecked(event.target.checked);
            onChange(event.target.checked ? TEST_MODE_TOKEN : null);
          }}
        />{" "}
        I am not a robot (reCAPTCHA test mode)
      </label>
      {error && (
        <p id="captcha-error" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}

function RecaptchaWidget({
  siteKey,
  resetSignal,
  error,
  onChange,
}: CaptchaProps) {
  const container = useRef<HTMLDivElement>(null);
  const widgetId = useRef<number | null>(null);
  const onChangeRef = useRef(onChange);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    let cancelled = false;
    loadRecaptcha()
      .then((grecaptcha) => {
        if (cancelled || !container.current || widgetId.current !== null) {
          return;
        }
        widgetId.current = grecaptcha.render(container.current, {
          sitekey: siteKey,
          callback: (token) => onChangeRef.current(token),
          "expired-callback": () => onChangeRef.current(null),
          "error-callback": () => onChangeRef.current(null),
        });
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [siteKey]);

  useEffect(() => {
    if (widgetId.current !== null && window.grecaptcha) {
      window.grecaptcha.reset(widgetId.current);
    }
  }, [resetSignal]);

  return (
    <div className="captcha">
      <div ref={container} />
      {loadError && (
        <p className="field-error" role="alert">
          The anti-robot check could not be loaded. Please reload the page.
        </p>
      )}
      {error && (
        <p id="captcha-error" className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
