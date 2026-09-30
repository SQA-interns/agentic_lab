import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const CONSENTS = [{ id: 'dp', text: 'I agree to data processing.', mandatory: true }];
const OPTIONS = {
  EXTERNAL: {
    type: 'EXTERNAL',
    options: [
      { id: 'ws', name: 'Workshop A', category: 'WORKSHOP' },
      { id: 'gala', name: 'Gala', category: 'EVENT' },
      { id: 'm1', name: 'Lunch 1', category: 'MEAL' },
    ],
    consents: CONSENTS,
    categoryLimits: { MEAL: 1 },
  },
  STUDENT: {
    type: 'STUDENT',
    options: [{ id: 'ws', name: 'Workshop A', category: 'WORKSHOP' }],
    consents: CONSENTS,
    categoryLimits: {},
  },
} as const;

type Handler = (url: string, init?: RequestInit) => Response | Promise<Response>;
let post: Handler;
let configStatus: number;
const bodies: unknown[] = [];

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  bodies.length = 0;
  configStatus = 200;
  post = () => json(500, { message: 'unused', errors: [] });
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      if (input === '/api/config') {
        return json(configStatus, {
          conferenceName: 'K',
          captchaTestMode: true,
          recaptchaSiteKey: '',
        });
      }
      const type = /type=(\w+)/.exec(input)?.[1] as keyof typeof OPTIONS | undefined;
      if (type) return json(200, OPTIONS[type]);
      bodies.push(JSON.parse(String(init?.body)));
      return post(input, init);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

async function fillStudent() {
  render(<App />);
  fireEvent.click(await screen.findByRole('radio', { name: 'Student' }));
  await screen.findByLabelText(/^Student ID/);
  for (const [label, value] of [
    [/^First name/, ' Luka '],
    [/^Last name/, 'Kovač'],
    [/^Email/, 'luka@x.si'],
    [/^Study institution/, 'UL'],
    [/^Study programme/, 'RI'],
    [/^Student ID/, '63'],
  ] as const) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  fireEvent.click(screen.getByRole('checkbox', { name: /^I agree to data processing/ }));
  fireEvent.click(screen.getByRole('checkbox', { name: /test mode/ }));
}

describe('App', () => {
  it('shows the conference name and a category limit hint', async () => {
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'K – registration' })).toBeInTheDocument();
    expect(await screen.findByText('Choose at most 1.')).toBeInTheDocument();
  });

  it('reports a configuration load failure', async () => {
    configStatus = 500;
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be loaded/);
  });

  it('sends only the student fields, trimmed, and shows no options when none were chosen', async () => {
    post = () =>
      json(201, {
        reference: 'ref-1',
        type: 'STUDENT',
        firstName: 'Luka',
        lastName: 'Kovač',
        email: 'luka@x.si',
        options: [],
        submittedAt: '2026-09-30T10:00:00Z',
      });
    await fillStudent();

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByText(/did not select any optional activities/)).toBeInTheDocument();
    expect(bodies[0]).toEqual({
      type: 'STUDENT',
      firstName: 'Luka',
      lastName: 'Kovač',
      email: 'luka@x.si',
      studyInstitution: 'UL',
      studyProgramme: 'RI',
      studentId: '63',
      optionIds: [],
      consentIds: ['dp'],
      captchaToken: 'test-mode-token',
    });
  });

  it('marks options and the captcha when the backend rejects them', async () => {
    post = () =>
      json(400, {
        message: 'Please correct the marked fields.',
        errors: [
          { field: 'optionIds', message: 'Too many options selected in one category.' },
          { field: 'captchaToken', message: 'The robot check failed.' },
        ],
      });
    await fillStudent();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Workshop A' }));

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: 'Workshop A' })).toHaveAttribute(
        'aria-invalid',
        'true',
      ),
    );
    expect(screen.getByText('Too many options selected in one category.')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /test mode/ })).not.toBeChecked();
    expect(screen.getByText('The robot check failed.')).toBeInTheDocument();
  });

  it('drops selections the new type does not offer', async () => {
    render(<App />);
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Gala' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Workshop A' }));

    fireEvent.click(screen.getByRole('radio', { name: 'Student' }));
    await waitFor(() => expect(screen.queryByRole('checkbox', { name: 'Gala' })).toBeNull());
    fireEvent.click(screen.getByRole('radio', { name: 'External participant' }));

    expect(await screen.findByRole('checkbox', { name: 'Gala' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Workshop A' })).toBeChecked();
  });
});
