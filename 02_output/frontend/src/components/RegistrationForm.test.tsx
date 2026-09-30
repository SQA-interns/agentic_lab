import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RegistrationForm } from './RegistrationForm';
import { catalog, jsonResponse } from '../test/fixtures';

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('First name'), ' Ana ');
  await user.type(screen.getByLabelText('Last name'), 'Kovač');
  await user.type(screen.getByLabelText('Email'), 'ana@example.test');
  await user.type(screen.getByLabelText('Organization / institution'), 'Org');
  await user.click(screen.getByLabelText('Workshop A'));
  await user.click(screen.getByLabelText(catalog.consent?.text ?? ''));
  await user.click(screen.getByLabelText('Local test captcha: I am not a robot'));
}

describe('RegistrationForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders groups, unchecked consent and no student fields for the external form', () => {
    render(<RegistrationForm form="external" catalog={catalog} onAccepted={vi.fn()} />);
    expect(
      screen.getByRole('heading', { name: 'External participant registration' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Workshops' })).toBeInTheDocument();
    expect(
      within(screen.getByRole('group', { name: 'Events' })).getByText('No options available.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(catalog.consent?.text ?? '')).not.toBeChecked();
    expect(screen.queryByLabelText('Student ID')).not.toBeInTheDocument();
  });

  it('shows a focused summary and linked field errors without sending', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const user = userEvent.setup();
    render(<RegistrationForm form="student" catalog={catalog} onAccepted={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    const summary = screen.getByRole('alert');
    expect(summary).toHaveFocus();
    expect(summary).toHaveTextContent('Please correct the following');
    const studentId = screen.getByLabelText('Student ID');
    expect(studentId).toHaveAttribute('aria-invalid', 'true');
    expect(studentId).toHaveAccessibleDescription(/Student ID is required/);
    expect(screen.getByLabelText('Local test captcha: I am not a robot')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('submits trimmed values with selections and reports acceptance', async () => {
    const accepted = {
      registrationId: 'id-1',
      clientRequestId: 'c',
      formType: 'external',
      status: 'ACCEPTED',
      acceptedAt: 't',
    };
    const fetchSpy = vi.fn().mockResolvedValue(jsonResponse(201, accepted));
    vi.stubGlobal('fetch', fetchSpy);
    const onAccepted = vi.fn();
    const user = userEvent.setup();
    render(<RegistrationForm form="external" catalog={catalog} onAccepted={onAccepted} />);
    await fillValid(user);
    await user.click(screen.getByLabelText('Workshop A'));
    await user.click(screen.getByLabelText('Workshop A'));
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(onAccepted).toHaveBeenCalledWith(accepted);
    const body = JSON.parse(
      (fetchSpy.mock.calls[0] as [string, RequestInit])[1].body as string,
    ) as Record<string, unknown>;
    expect(body).toMatchObject({
      firstName: 'Ana',
      captchaToken: 'local-captcha-ok',
      consentGiven: true,
      selections: { workshops: ['ws-a'], events: [], meals: [], other: [] },
    });
    expect(body.clientRequestId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('maps server field errors and general errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          errors: [
            {
              field: 'email',
              code: 'INVALID_EMAIL',
              message: 'Email: enter a valid email address.',
            },
            { field: 'organization', code: 'TOO_LONG', message: 'too long' },
            {
              field: 'captchaToken',
              code: 'CAPTCHA_FAILED',
              message: 'Captcha verification failed.',
            },
            {
              field: 'selections.workshops',
              code: 'UNKNOWN_OPTION',
              message: 'A selected option is not available.',
            },
            { field: 'body', code: 'MALFORMED_REQUEST', message: 'Bad body.' },
          ],
        }),
      ),
    );
    const user = userEvent.setup();
    render(<RegistrationForm form="external" catalog={catalog} onAccepted={vi.fn()} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      /Email: enter a valid email address/,
    );
    expect(screen.getByLabelText('Organization / institution')).toHaveAccessibleDescription(
      /Organization \/ institution: too long/,
    );
    const summary = screen.getByRole('alert');
    expect(summary).toHaveTextContent('Activities: A selected option is not available.');
    expect(summary).toHaveTextContent('Bad body.');
    expect(summary).toHaveTextContent('Captcha verification failed.');
  });

  it.each([
    [503, 'Your registration was not saved'],
    [409, 'conflicts with an earlier one'],
    [429, 'Too many registration attempts'],
  ])('shows a save error for status %i and keeps the data', async (status, text) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(status, {})));
    const user = userEvent.setup();
    render(<RegistrationForm form="external" catalog={catalog} onAccepted={vi.fn()} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(screen.getByRole('alert')).toHaveTextContent(text);
    expect(screen.getByLabelText('Email')).toHaveValue('ana@example.test');
  });

  it('reuses the client request ID when retrying after a failure', async () => {
    const fetchSpy = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(503, {}))
      .mockResolvedValueOnce(jsonResponse(201, { registrationId: 'x' }));
    vi.stubGlobal('fetch', fetchSpy);
    const onAccepted = vi.fn();
    const user = userEvent.setup();
    render(<RegistrationForm form="external" catalog={catalog} onAccepted={onAccepted} />);
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    const ids = fetchSpy.mock.calls.map(
      (c) =>
        (JSON.parse((c as [string, RequestInit])[1].body as string) as { clientRequestId: string })
          .clientRequestId,
    );
    expect(ids[0]).toBe(ids[1]);
    expect(onAccepted).toHaveBeenCalledTimes(1);
  });

  it('renders student fields and hides consent when the catalog has none', () => {
    render(
      <RegistrationForm
        form="student"
        catalog={{ ...catalog, consent: null }}
        onAccepted={vi.fn()}
      />,
    );
    for (const label of ['Study institution', 'Study programme', 'Student ID']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(catalog.consent?.text ?? '')).not.toBeInTheDocument();
  });
});
