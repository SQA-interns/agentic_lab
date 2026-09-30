import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { catalog, jsonResponse } from './test/fixtures';

describe('App routing', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('home lists both forms and the organizer page', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, catalog)));
    render(<App path="/" />);
    expect(await screen.findByRole('heading', { name: 'Lab Conference' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'External participant registration' })).toHaveAttribute(
      'href',
      '/register/external',
    );
    expect(screen.getByRole('link', { name: 'Student registration' })).toHaveAttribute(
      'href',
      '/register/student',
    );
    expect(screen.getByRole('link', { name: 'Organizer export' })).toHaveAttribute(
      'href',
      '/organizer',
    );
  });

  it('organizer page links the export without fetching', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    render(<App path="/organizer" />);
    expect(screen.getByRole('link', { name: 'Download Excel export' })).toHaveAttribute(
      'href',
      '/api/organizer/export.xlsx',
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('unknown paths show not found', () => {
    render(<App path="/nope" />);
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('catalog failure shows an alert', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(502, {})));
    render(<App path="/register/student" />);
    expect(await screen.findByRole('alert')).toHaveTextContent('cannot be reached');
  });

  it('shows the confirmation only after acceptance', async () => {
    const fetchSpy = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(200, catalog))
      .mockResolvedValueOnce(
        jsonResponse(201, {
          registrationId: 'reg-123',
          clientRequestId: 'c',
          formType: 'external',
          status: 'ACCEPTED',
          acceptedAt: 't',
        }),
      );
    vi.stubGlobal('fetch', fetchSpy);
    const user = userEvent.setup();
    render(<App path="/register/external" />);
    await user.type(await screen.findByLabelText('First name'), 'Ana');
    await user.type(screen.getByLabelText('Last name'), 'K');
    await user.type(screen.getByLabelText('Email'), 'a@example.test');
    await user.type(screen.getByLabelText('Organization / institution'), 'O');
    await user.click(screen.getByLabelText(catalog.consent?.text ?? ''));
    await user.click(screen.getByLabelText('Local test captcha: I am not a robot'));
    expect(
      screen.queryByRole('heading', { name: 'Registration received' }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Submit registration' }));
    expect(await screen.findByRole('heading', { name: 'Registration received' })).toHaveFocus();
    expect(screen.getByTestId('registration-id')).toHaveTextContent('reg-123');
    expect(document.title).toContain('Registration received');
  });
});
