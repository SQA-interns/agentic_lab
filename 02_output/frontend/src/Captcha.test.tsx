import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Captcha, TEST_MODE_TOKEN } from './Captcha';

afterEach(() => {
  document.getElementById('recaptcha-script')?.remove();
  delete window.grecaptcha;
  delete window.onRecaptchaLoaded;
});

describe('Captcha', () => {
  it('test mode yields the fixed token and clears it again', () => {
    const onToken = vi.fn();
    const { rerender } = render(
      <Captcha testMode siteKey="" token="" resetCount={0} onToken={onToken} />,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: /test mode/ }));
    expect(onToken).toHaveBeenLastCalledWith(TEST_MODE_TOKEN);

    rerender(
      <Captcha testMode siteKey="" token={TEST_MODE_TOKEN} resetCount={0} onToken={onToken} />,
    );
    fireEvent.click(screen.getByRole('checkbox', { name: /test mode/ }));
    expect(onToken).toHaveBeenLastCalledWith('');
  });

  it('shows its error next to the checkbox', () => {
    render(
      <Captcha testMode siteKey="" token="" resetCount={0} error="Confirm" onToken={vi.fn()} />,
    );
    expect(screen.getByRole('checkbox')).toHaveAccessibleDescription('Confirm');
  });

  it('loads the Google script once and renders the widget with the site key', () => {
    const { unmount } = render(
      <Captcha testMode={false} siteKey="site-key" token="" resetCount={0} onToken={vi.fn()} />,
    );
    const script = document.getElementById('recaptcha-script') as HTMLScriptElement;
    expect(script.src).toBe(
      'https://www.google.com/recaptcha/api.js?onload=onRecaptchaLoaded&render=explicit',
    );
    expect(screen.queryByRole('checkbox')).toBeNull();

    const renderWidget = vi.fn(() => 7);
    window.grecaptcha = { render: renderWidget, reset: vi.fn() };
    window.onRecaptchaLoaded?.();
    expect(renderWidget).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ sitekey: 'site-key' }),
    );
    unmount();
  });

  it('renders immediately when the script is loaded and resets on request', () => {
    const onToken = vi.fn();
    const reset = vi.fn();
    let options: { callback: (t: string) => void; 'expired-callback': () => void } | undefined;
    window.grecaptcha = {
      render: vi.fn((_el, o) => {
        options = o;
        return 3;
      }),
      reset,
    };
    const { rerender } = render(
      <Captcha testMode={false} siteKey="k" token="" resetCount={0} onToken={onToken} />,
    );
    options?.callback('google-token');
    expect(onToken).toHaveBeenLastCalledWith('google-token');
    options?.['expired-callback']();
    expect(onToken).toHaveBeenLastCalledWith('');

    rerender(<Captcha testMode={false} siteKey="k" token="" resetCount={1} onToken={onToken} />);
    expect(reset).toHaveBeenCalledWith(3);
    expect(document.getElementById('recaptcha-script')).toBeNull();
  });
});
