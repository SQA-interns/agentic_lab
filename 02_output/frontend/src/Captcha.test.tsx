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
