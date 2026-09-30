import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Captcha, STUB_LABEL, STUB_TOKEN } from './Captcha';

describe('Captcha', () => {
  afterEach(() => {
    delete window.grecaptcha;
    delete window.onRecaptchaLoad;
    document.head.innerHTML = '';
  });

  it('stub checkbox toggles the deterministic token', async () => {
    const onToken = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(<Captcha mode="stub" siteKey={null} token="" onToken={onToken} />);
    await user.click(screen.getByLabelText(STUB_LABEL));
    expect(onToken).toHaveBeenLastCalledWith(STUB_TOKEN);
    rerender(
      <Captcha
        mode="stub"
        siteKey={null}
        token={STUB_TOKEN}
        onToken={onToken}
        error="Please confirm the captcha."
      />,
    );
    expect(screen.getByLabelText(STUB_LABEL)).toBeChecked();
    expect(screen.getByLabelText(STUB_LABEL)).toHaveAccessibleDescription(/Please confirm/);
    await user.click(screen.getByLabelText(STUB_LABEL));
    expect(onToken).toHaveBeenLastCalledWith('');
  });

  it('loads the Google widget script in recaptcha mode', () => {
    render(
      <Captcha
        mode="recaptcha"
        siteKey="site"
        token=""
        onToken={vi.fn()}
        error="Captcha failed."
      />,
    );
    const script = document.head.querySelector('script');
    expect(script?.src).toContain('https://www.google.com/recaptcha/api.js');
    expect(screen.getByText('Captcha failed.')).toBeInTheDocument();
    expect(screen.queryByLabelText(STUB_LABEL)).not.toBeInTheDocument();
  });

  it('renders the widget when the library is present and forwards tokens', () => {
    const onToken = vi.fn();
    const render_ = vi.fn((_el: HTMLElement, options: Record<string, unknown>) => {
      (options.callback as (t: string) => void)('tok');
      (options['expired-callback'] as () => void)();
      return 1;
    });
    window.grecaptcha = { render: render_ };
    render(<Captcha mode="recaptcha" siteKey="site" token="" onToken={onToken} />);
    expect(render_).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({ sitekey: 'site' }),
    );
    expect(onToken).toHaveBeenNthCalledWith(1, 'tok');
    expect(onToken).toHaveBeenNthCalledWith(2, '');
  });
});
