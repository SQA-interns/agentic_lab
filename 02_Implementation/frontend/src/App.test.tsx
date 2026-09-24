import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { jsonResponse, mockBackend } from './test/fetchMock';

describe('App routing', () => {
  it('offers both registration variants on the start page', () => {
    render(<App path="/" />);
    expect(screen.getByRole('link', { name: /External participant/ })).toHaveAttribute(
      'href',
      '/register/external',
    );
    expect(screen.getByRole('link', { name: /Student/ })).toHaveAttribute(
      'href',
      '/register/student',
    );
  });

  it('renders the student form on its route (trailing slash tolerated)', async () => {
    mockBackend(() => jsonResponse(201, {}));
    render(<App path="/register/student/" />);
    expect(
      await screen.findByRole('heading', { name: 'Student registration' }),
    ).toBeInTheDocument();
  });

  it('falls back to the start page for unknown paths', () => {
    render(<App path="/unknown" />);
    expect(screen.getByRole('heading', { name: 'Choose your registration' })).toBeInTheDocument();
  });
});
