import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { bodyOf, jsonResponse, mockBackend, submitCalls } from '../test/fetchMock';
import { RegistrationForm } from './RegistrationForm';

const CREATED = { id: '11111111-2222-3333-4444-555555555555', type: 'EXTERNAL', createdAt: 'x' };

async function fillExternal(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^First name/), '  Žiga ');
  await user.type(screen.getByLabelText(/^Last name/), 'Čeh');
  await user.type(screen.getByLabelText(/^Email/), 'ziga@example.si');
  await user.type(screen.getByLabelText(/^Organization/), 'Univerza v Mariboru');
}

async function renderReady(type: 'EXTERNAL' | 'STUDENT' = 'EXTERNAL') {
  render(<RegistrationForm type={type} />);
  await screen.findByText('Workshop A');
  await waitFor(() => {
    expect(screen.getByRole('button', { name: 'Register' })).toBeEnabled();
  });
}

describe('RegistrationForm – external (US-001)', () => {
  it('shows exactly the external fixed fields (AC-001-02)', async () => {
    mockBackend(() => jsonResponse(201, CREATED));
    await renderReady();
    const textboxes = screen.getAllByRole('textbox').filter((el) => el.id !== 'field-website');
    expect(textboxes.map((el) => el.getAttribute('name'))).toEqual([
      'firstName',
      'lastName',
      'email',
      'organization',
    ]);
    expect(screen.queryByLabelText(/Student ID/)).not.toBeInTheDocument();
  });

  it('does not preselect the mandatory consent (AC-001-06)', async () => {
    mockBackend(() => jsonResponse(201, CREATED));
    await renderReady();
    expect(screen.getByRole('checkbox', { name: /personal data/ })).not.toBeChecked();
  });

  it('blocks submission of an empty required field and shows a message (AC-001-03)', async () => {
    const calls = mockBackend(() => jsonResponse(201, CREATED));
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.clear(screen.getByLabelText(/^Organization/));
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText('This field is required.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Organization/)).toHaveAttribute('aria-invalid', 'true');
    expect(submitCalls(calls)).toHaveLength(0);
  });

  it('blocks submission without consent (AC-001-05)', async () => {
    const calls = mockBackend(() => jsonResponse(201, CREATED));
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(await screen.findByText('You must accept the privacy statement.')).toBeInTheDocument();
    expect(submitCalls(calls)).toHaveLength(0);
  });

  it('displays active options grouped by category (AC-003-01)', async () => {
    mockBackend(() => jsonResponse(201, CREATED));
    await renderReady();
    const workshops = screen.getByRole('group', { name: 'Workshops' });
    expect(within(workshops).getByLabelText('Workshop A')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Events' })).toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Meals' })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Other activities' })).not.toBeInTheDocument();
  });

  it('submits trimmed data and shows confirmation only after 201 (AC-001-01, AC-004-01)', async () => {
    const calls = mockBackend(() => jsonResponse(201, CREATED));
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByLabelText('Workshop A'));
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(
      await screen.findByRole('heading', { name: 'Registration received' }),
    ).toBeInTheDocument();
    expect(screen.getByText(CREATED.id)).toBeInTheDocument();
    const [post, ...others] = submitCalls(calls);
    expect(others).toHaveLength(0);
    if (post === undefined) {
      throw new Error('expected one POST');
    }
    expect(post.url).toBe('/api/registrations/external');
    expect(bodyOf(post)).toEqual({
      firstName: 'Žiga',
      lastName: 'Čeh',
      email: 'ziga@example.si',
      organization: 'Univerza v Mariboru',
      privacyConsent: true,
      optionIds: ['ws-a'],
      formToken: 'token-1',
      website: '',
    });
  });

  it('shows backend field errors and no confirmation on 400 (AC-004-02)', async () => {
    mockBackend(() =>
      jsonResponse(400, {
        status: 400,
        error: 'VALIDATION_FAILED',
        message: 'x',
        fieldErrors: { email: 'Please enter a valid email address.' },
      }),
    );
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText('Please enter a valid email address.')).toBeInTheDocument();
    expect(screen.queryByText('Registration received')).not.toBeInTheDocument();
  });

  it('shows a general error and keeps data on server error (AC-004-03)', async () => {
    mockBackend(() => jsonResponse(500, { status: 500, error: 'INTERNAL_ERROR', message: 'x' }));
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/server error/);
    expect(screen.getByLabelText(/^Last name/)).toHaveValue('Čeh');
    expect(screen.queryByText('Registration received')).not.toBeInTheDocument();
  });

  it('shows a network error when the backend is unreachable (AC-004-03)', async () => {
    mockBackend(() => Promise.reject(new TypeError('Failed to fetch')));
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be reached/);
  });

  it('disables submit while a request is in flight (AC-004-04)', async () => {
    let resolve: (r: Response) => void = () => undefined;
    const calls = mockBackend(
      () =>
        new Promise<Response>((r) => {
          resolve = r;
        }),
    );
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));

    const button = screen.getByRole('button', { name: 'Submitting…' });
    expect(button).toBeDisabled();
    await user.click(button);
    expect(submitCalls(calls)).toHaveLength(1);
    resolve(jsonResponse(201, CREATED));
    expect(await screen.findByText('Registration received')).toBeInTheDocument();
  });

  it('fetches a new form token after an anti-automation rejection', async () => {
    const calls = mockBackend(() =>
      jsonResponse(400, { status: 400, error: 'SUBMISSION_REJECTED', message: 'x' }),
    );
    const user = userEvent.setup();
    await renderReady();
    await fillExternal(user);
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be accepted/);
    await waitFor(() => {
      expect(calls.filter((c) => c.url === '/api/form-token')).toHaveLength(2);
    });
  });

  it('shows a load error when options cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse(429, { error: 'RATE_LIMITED' }))),
    );
    render(<RegistrationForm type="EXTERNAL" />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be loaded/);
  });
});

describe('RegistrationForm – student (US-002)', () => {
  it('shows exactly the student fixed fields (AC-002-02)', async () => {
    mockBackend(() => jsonResponse(201, CREATED));
    await renderReady('STUDENT');
    const names = screen
      .getAllByRole('textbox')
      .filter((el) => el.id !== 'field-website')
      .map((el) => el.getAttribute('name'));
    expect(names).toEqual([
      'firstName',
      'lastName',
      'email',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);
  });

  it('blocks a malformed email with a message (AC-002-04)', async () => {
    const calls = mockBackend(() => jsonResponse(201, CREATED));
    const user = userEvent.setup();
    await renderReady('STUDENT');
    await user.type(screen.getByLabelText(/^Email/), 'not-an-email');
    await user.tab();
    expect(await screen.findByText('Please enter a valid email address.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(submitCalls(calls)).toHaveLength(0);
  });

  it('posts to the student endpoint', async () => {
    const calls = mockBackend(() => jsonResponse(201, { ...CREATED, type: 'STUDENT' }));
    const user = userEvent.setup();
    await renderReady('STUDENT');
    await user.type(screen.getByLabelText(/^First name/), 'Špela');
    await user.type(screen.getByLabelText(/^Last name/), 'Kovač');
    await user.type(screen.getByLabelText(/^Email/), 'spela@student.um.si');
    await user.type(screen.getByLabelText(/^Study institution/), 'FERI');
    await user.type(screen.getByLabelText(/^Study programme/), 'Informatika');
    await user.type(screen.getByLabelText(/^Student ID/), 'E1234567');
    await user.click(screen.getByRole('checkbox', { name: /personal data/ }));
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(await screen.findByText('Registration received')).toBeInTheDocument();
    expect(submitCalls(calls)[0]?.url).toBe('/api/registrations/student');
  });
});
