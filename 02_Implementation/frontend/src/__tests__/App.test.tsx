import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { CONFIG, jsonResponse } from './fixtures';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('loads the backend form configuration and switches between forms', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, CONFIG)));
    const user = userEvent.setup();
    render(<App />);
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      'Test Conference registration',
    );
    expect(screen.getByLabelText(/Organization/)).toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Student' }));
    expect(screen.getByLabelText(/Student ID/)).toBeInTheDocument();
  });

  it('shows an error when configuration cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(503, {})));
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent('unavailable');
  });
});
