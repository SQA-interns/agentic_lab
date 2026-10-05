import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { FormConfig } from './contract';

const config: FormConfig = {
  conferenceName: 'Konferenca 2026',
  consent: { id: 'c1', text: 'I agree to the processing.' },
  options: [
    { id: 'ws-a', name: 'Delavnica A', category: 'workshop' },
    { id: 'meal-b', name: 'Kosilo', category: 'meal' },
  ],
  captcha: { testMode: true, siteKey: null },
};

type Reply = { status: number; body: unknown };

function backend(...registrationReplies: Reply[]) {
  const posts: unknown[] = [];
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/form-config') {
      return Promise.resolve(new Response(JSON.stringify(config), { status: 200 }));
    }
    posts.push(JSON.parse(init?.body as string));
    const reply = registrationReplies.shift() ?? { status: 500, body: {} };
    return Promise.resolve(new Response(JSON.stringify(reply.body), { status: reply.status }));
  });
  vi.stubGlobal('fetch', fetchMock);
  return posts;
}

async function openForm() {
  render(<App />);
  await screen.findByRole('radiogroup', { name: 'Registration type' });
}

function fillExternal(email = 'ana@example.si') {
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: ' Ana ' } });
  fireEvent.change(screen.getByLabelText('Last name'), { target: { value: 'Žagar' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
  fireEvent.change(screen.getByLabelText('Organization / institution'), {
    target: { value: 'IJS' },
  });
}

function consentAndCaptcha() {
  fireEvent.click(screen.getByRole('checkbox', { name: config.consent.text }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }));
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: 'Register' }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('registration page', () => {
  it('shows the external form, options by category and an unchecked consent', async () => {
    backend();
    await openForm();

    expect(screen.getByText('Konferenca 2026')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'External participant' })).toBeChecked();
    expect(screen.queryByLabelText('Student ID')).not.toBeInTheDocument();
    const workshops = screen.getByRole('group', { name: 'Workshops' });
    expect(within(workshops).getByRole('checkbox', { name: 'Delavnica A' })).not.toBeChecked();
    expect(screen.getByRole('group', { name: 'Meals' })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Events' })).not.toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: config.consent.text })).not.toBeChecked();
  });

  it('switches to the student fields', async () => {
    backend();
    await openForm();
    fireEvent.click(screen.getByRole('radio', { name: 'Student' }));

    for (const label of ['Study institution', 'Study programme', 'Student ID']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText('Organization / institution')).not.toBeInTheDocument();
  });

  it('validates before submitting and sends nothing when invalid (NFR-03)', async () => {
    const posts = backend();
    await openForm();
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ana@' } });
    submit();

    const first = screen.getByLabelText('First name');
    expect(first).toHaveAttribute('aria-invalid', 'true');
    expect(first).toHaveAccessibleDescription('This field is required.');
    expect(first).toHaveFocus();
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address.',
    );
    expect(screen.getByText('You must give this consent to register.')).toBeInTheDocument();
    expect(screen.getByText('Please confirm that you are not a robot.')).toBeInTheDocument();
    expect(posts).toHaveLength(0);
  });

  it('sends the trimmed-by-backend values with options in configuration order', async () => {
    const posts = backend({ status: 201, body: { id: 'i', type: 'EXTERNAL', acceptedAt: 'a' } });
    await openForm();
    fillExternal();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Kosilo' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Delavnica A' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Kosilo' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Kosilo' }));
    consentAndCaptcha();
    submit();

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('Registration received');
    expect(status).toHaveTextContent('Thank you, Ana.');
    expect(status).toHaveTextContent('ana@example.si');
    expect(screen.queryByRole('button', { name: 'Register' })).not.toBeInTheDocument();
    expect(posts[0]).toEqual({
      type: 'EXTERNAL',
      firstName: ' Ana ',
      lastName: 'Žagar',
      email: 'ana@example.si',
      organization: 'IJS',
      optionIds: ['ws-a', 'meal-b'],
      consentGiven: true,
      captchaToken: 'test-pass',
    });
  });

  it('shows backend field errors next to the fields and resets the captcha (NFR-03)', async () => {
    backend({
      status: 400,
      body: {
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'lastName', code: 'INVALID_CHARACTERS' },
          { field: 'captchaToken', code: 'CAPTCHA_FAILED' },
        ],
      },
    });
    await openForm();
    fillExternal();
    consentAndCaptcha();
    submit();

    await waitFor(() =>
      expect(screen.getByLabelText('Last name')).toHaveAccessibleDescription(
        'This value contains characters that are not allowed.',
      ),
    );
    expect(screen.getByText('Please confirm that you are not a robot.')).toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }),
    ).not.toBeChecked();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it.each([
    [409, 'EMAIL_ALREADY_REGISTERED', 'This email address is already registered.'],
    [503, 'STORAGE_UNAVAILABLE', 'Your registration could not be saved.'],
    [429, 'RATE_LIMITED', 'Too many attempts.'],
  ])('shows a form error for status %i', async (status, code, text) => {
    backend({ status, body: { code } });
    await openForm();
    fillExternal();
    consentAndCaptcha();
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(text);
    expect(screen.getByLabelText('Email')).toHaveValue('ana@example.si');
  });

  it('sends only checked options and marks the consent and captcha errors', async () => {
    const posts = backend(
      { status: 503, body: { code: 'STORAGE_UNAVAILABLE' } },
      { status: 201, body: { id: 'i', type: 'EXTERNAL', acceptedAt: 'a' } },
    );
    await openForm();
    fillExternal();
    submit();
    expect(screen.getByRole('checkbox', { name: config.consent.text })).toHaveAccessibleDescription(
      'You must give this consent to register.',
    );
    expect(
      screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }),
    ).toHaveAccessibleDescription('Please confirm that you are not a robot.');

    fireEvent.click(screen.getByRole('checkbox', { name: 'Delavnica A' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Kosilo' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Delavnica A' }));
    consentAndCaptcha();
    submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    expect(screen.getByRole('checkbox', { name: config.consent.text })).not.toHaveAttribute(
      'aria-invalid',
    );

    fireEvent.click(screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }));
    submit();
    await screen.findByRole('status');
    expect(posts).toHaveLength(2);
    expect((posts[1] as { optionIds: string[] }).optionIds).toEqual(['meal-b']);
  });

  it('clears the previous form error when submitting again', async () => {
    backend(
      { status: 409, body: { code: 'EMAIL_ALREADY_REGISTERED' } },
      {
        status: 400,
        body: { code: 'VALIDATION_FAILED', errors: [{ field: 'email', code: 'INVALID_EMAIL' }] },
      },
    );
    await openForm();
    fillExternal();
    consentAndCaptcha();
    submit();
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('checkbox', { name: 'I am not a robot (test mode)' }));
    submit();
    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
        'Enter a valid email address.',
      ),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows option errors from the backend as a form error', async () => {
    backend({
      status: 400,
      body: {
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'optionIds', code: 'INACTIVE_OPTION' }],
      },
    });
    await openForm();
    fillExternal();
    consentAndCaptcha();
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'One of the selected options is not available.',
    );
  });

  it('shows a generic error when the form configuration cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })));
    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong.');
    expect(screen.queryByRole('button', { name: 'Register' })).not.toBeInTheDocument();
  });
});
