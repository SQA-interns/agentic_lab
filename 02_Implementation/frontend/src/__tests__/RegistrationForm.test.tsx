import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RegistrationForm } from '../RegistrationForm';
import { ACCEPTED, CONFIG, jsonResponse } from './fixtures';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

async function fillExternal(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/First name/), ' Špela ');
  await user.type(screen.getByLabelText(/Last name/), 'Novak');
  await user.type(screen.getByLabelText(/Email/), 'spela@example.org');
  await user.type(screen.getByLabelText(/Organization/), 'Inštitut');
  await user.click(screen.getByLabelText('Workshop B'));
  await user.click(screen.getByLabelText(/Synthetic consent/));
  await user.click(screen.getByLabelText(/I am not a robot/));
}

describe('RegistrationForm', () => {
  it('renders fixed external fields, active options and an unchecked consent', () => {
    render(<RegistrationForm type="external" config={CONFIG} />);
    for (const label of [/First name/, /Last name/, /Email/, /Organization/]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/Student ID/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Workshop A')).not.toBeChecked();
    expect(screen.getByLabelText(/Synthetic consent/)).not.toBeChecked();
    expect(screen.queryByRole('group', { name: 'Other activities' })).not.toBeInTheDocument();
  });

  it('renders the six student fields', () => {
    render(<RegistrationForm type="student" config={CONFIG} />);
    for (const label of [/Study institution/, /Study programme/, /Student ID/]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.queryByLabelText(/Organization/)).not.toBeInTheDocument();
  });

  it('shows success only after the backend accepts, with trimmed payload', async () => {
    const user = userEvent.setup();
    let resolveResponse: (r: Response) => void = () => {};
    fetchMock.mockReturnValue(new Promise<Response>((resolve) => (resolveResponse = resolve)));
    render(<RegistrationForm type="external" config={CONFIG} />);
    await fillExternal(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));

    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/registrations/external');
    const sent = JSON.parse(String(init?.body));
    expect(sent.firstName).toBe('Špela');
    expect(sent.selections.workshops).toEqual(['ws-b']);
    expect(sent.consents).toEqual({ 'synthetic-consent': true });
    expect(sent.captchaToken).toBe('test-token');
    expect(sent.clientRequestId).toMatch(/^[0-9a-f-]{36}$/);

    resolveResponse(jsonResponse(201, ACCEPTED));
    expect(await screen.findByRole('status')).toHaveTextContent('Registration received');
    expect(screen.getByTestId('registration-id')).toHaveTextContent(ACCEPTED.registrationId);
    expect(screen.getByRole('status')).toHaveTextContent('queued');
  });

  it('blocks submission without the required consent and captcha', async () => {
    const user = userEvent.setup();
    render(<RegistrationForm type="external" config={CONFIG} />);
    await user.type(screen.getByLabelText(/First name/), 'Ana');
    await user.type(screen.getByLabelText(/Last name/), 'Novak');
    await user.type(screen.getByLabelText(/Email/), 'ana@example.org');
    await user.type(screen.getByLabelText(/Organization/), 'Org');
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(screen.getByText('This consent is required.')).toBeInTheDocument();
    await user.click(screen.getByLabelText(/Synthetic consent/));
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(screen.getByRole('alert')).toHaveTextContent('anti-robot');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    [400, { code: 'VALIDATION_FAILED', errors: [{ field: 'email', code: 'EMAIL_INVALID' }] }],
    [400, { code: 'CAPTCHA_INVALID' }],
    [503, { code: 'STORAGE_UNAVAILABLE' }],
    [429, { code: 'RATE_LIMITED' }],
    [500, { code: 'INTERNAL_ERROR' }],
  ])('never shows success for HTTP %i', async (status, body) => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(status, body));
    render(<RegistrationForm type="external" config={CONFIG} />);
    await fillExternal(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/I am not a robot/)).not.toBeChecked();
  });

  it('reports network failure without success', async () => {
    const user = userEvent.setup();
    fetchMock.mockRejectedValue(new TypeError('network down'));
    render(<RegistrationForm type="external" config={CONFIG} />);
    await fillExternal(user);
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be reached');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('reuses the request ID for an unchanged retry and renews it after an edit', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(503, { code: 'STORAGE_UNAVAILABLE' }));
    render(<RegistrationForm type="external" config={CONFIG} />);
    await fillExternal(user);
    const submit = () => user.click(screen.getByRole('button', { name: 'Submit registration' }));
    await submit();
    await screen.findByRole('alert');
    await user.click(screen.getByLabelText(/I am not a robot/));
    await submit();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await user.type(screen.getByLabelText(/Organization/), ' 2');
    await user.click(screen.getByLabelText(/I am not a robot/));
    await submit();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    const ids = fetchMock.mock.calls.map(
      ([, init]) => JSON.parse(String(init?.body)).clientRequestId as string,
    );
    expect(ids[0]).toBe(ids[1]);
    expect(ids[2]).not.toBe(ids[1]);
  });
});
