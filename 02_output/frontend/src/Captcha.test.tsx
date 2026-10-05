import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Captcha } from './Captcha';

afterEach(() => {
  delete window.grecaptcha;
  delete window.onRecaptchaLoaded;
  document.getElementById('recaptcha-script')?.remove();
});

describe('Captcha', () => {
  it('in test mode toggles the passing token', () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha testMode siteKey={null} token={null} resetCount={0} onToken={onToken} />,
    );
    const box = screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' });
    box.click();
    expect(onToken).toHaveBeenLastCalledWith('test-pass');

    rerender(
      <Captcha testMode siteKey={null} token="test-pass" resetCount={0} onToken={onToken} />,
    );
    screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }).click();
    expect(onToken).toHaveBeenLastCalledWith(null);
    expect(document.getElementById('recaptcha-script')).toBeNull();
  });

  it('loads the Google script once and renders the widget with the site key', () => {
    const onToken = vi.fn();
    render(
      <Captcha testMode={false} siteKey="site-key" token={null} resetCount={0} onToken={onToken} />,
    );
    const script = document.getElementById('recaptcha-script') as HTMLScriptElement;
    expect(script.src).toContain('https://www.google.com/recaptcha/api.js?render=explicit');

    const renderWidget = vi.fn().mockReturnValue(7);
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    window.onRecaptchaLoaded?.();

    expect(renderWidget).toHaveBeenCalledTimes(1);
    const [, parameters] = renderWidget.mock.calls[0];
    expect(parameters.sitekey).toBe('site-key');
    parameters.callback('google-token');
    expect(onToken).toHaveBeenLastCalledWith('google-token');
    parameters['expired-callback']();
    expect(onToken).toHaveBeenLastCalledWith(null);
  });

  it('never renders the Google widget in test mode or without a site key', () => {
    const renderWidget = vi.fn().mockReturnValue(1);
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    render(<Captcha testMode siteKey="k" token={null} resetCount={0} onToken={vi.fn()} />);
    render(
      <Captcha testMode={false} siteKey={null} token={null} resetCount={0} onToken={vi.fn()} />,
    );

    expect(renderWidget).not.toHaveBeenCalled();
    expect(document.getElementById('recaptcha-script')).toBeNull();
  });

  it('adds the script only once and renders one widget per container', () => {
    render(<Captcha testMode={false} siteKey="k" token={null} resetCount={0} onToken={vi.fn()} />);
    render(<Captcha testMode={false} siteKey="k" token={null} resetCount={0} onToken={vi.fn()} />);
    expect(document.querySelectorAll('#recaptcha-script')).toHaveLength(1);

    const renderWidget = vi.fn((element: HTMLElement) => {
      element.appendChild(document.createElement('iframe'));
      return 5;
    });
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    window.onRecaptchaLoaded?.();
    window.onRecaptchaLoaded?.();
    expect(renderWidget).toHaveBeenCalledTimes(1);
  });

  it('does not reset without a widget and links the error description', () => {
    const { rerender } = render(
      <Captcha testMode={false} siteKey="k" token={null} resetCount={0} onToken={vi.fn()} />,
    );
    rerender(
      <Captcha testMode={false} siteKey="k" token={null} resetCount={2} onToken={vi.fn()} />,
    );
    render(
      <Captcha
        testMode
        siteKey={null}
        token={null}
        resetCount={0}
        errorId="e1"
        onToken={vi.fn()}
      />,
    );

    const box = screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' });
    expect(box).toHaveAttribute('aria-invalid', 'true');
    expect(box).toHaveAttribute('aria-describedby', 'e1');
  });

  it('renders at once when the script is loaded and resets the widget on request', () => {
    const reset = vi.fn();
    window.grecaptcha = { render: vi.fn().mockReturnValue(3), reset };
    const { rerender } = render(
      <Captcha testMode={false} siteKey="k" token={null} resetCount={0} onToken={vi.fn()} />,
    );
    expect(window.grecaptcha.render).toHaveBeenCalledTimes(1);
    expect(reset).not.toHaveBeenCalled();

    rerender(
      <Captcha testMode={false} siteKey="k" token={null} resetCount={1} onToken={vi.fn()} />,
    );
    expect(reset).toHaveBeenCalledWith(3);
  });
});
