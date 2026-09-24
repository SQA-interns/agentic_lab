import { vi } from 'vitest';

export interface Call {
  url: string;
  init: RequestInit | undefined;
}

type Responder = (url: string, init: RequestInit | undefined) => Response | Promise<Response>;

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export const OPTIONS = [
  { id: 'ws-a', category: 'WORKSHOP', name: 'Workshop A' },
  { id: 'ev-b', category: 'EVENT', name: 'Opening reception' },
  { id: 'meal-c', category: 'MEAL', name: 'Lunch' },
];

/**
 * Installs a fetch mock that serves options and form tokens and delegates registration POSTs to
 * {@code onSubmit}. Returns the list of recorded calls.
 */
export function mockBackend(onSubmit: Responder, options: unknown = OPTIONS): Call[] {
  const calls: Call[] = [];
  let tokenCounter = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      calls.push({ url, init });
      if (url.startsWith('/api/options')) {
        return Promise.resolve(jsonResponse(200, options));
      }
      if (url === '/api/form-token') {
        tokenCounter += 1;
        return Promise.resolve(jsonResponse(200, { token: `token-${String(tokenCounter)}` }));
      }
      return Promise.resolve(onSubmit(url, init));
    }),
  );
  return calls;
}

export function submitCalls(calls: Call[]): Call[] {
  return calls.filter((c) => c.init?.method === 'POST');
}

export function bodyOf(call: Call): Record<string, unknown> {
  return JSON.parse(call.init?.body as string) as Record<string, unknown>;
}
