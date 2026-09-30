import { request } from '@playwright/test';

// Waits until the proxy and the backend behind it answer, so tests never run against a
// half-started stack (a setup error, not a behavioural failure).
export default async function globalSetup(): Promise<void> {
  const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18080';
  const context = await request.newContext({ baseURL });
  const deadline = Date.now() + 180_000;
  try {
    for (;;) {
      try {
        const health = await context.get('/healthz');
        const api = await context.get('/api/catalog');
        if (health.ok() && ![502, 503, 504].includes(api.status())) return;
      } catch {
        // stack not reachable yet
      }
      if (Date.now() > deadline) throw new Error(`stack at ${baseURL} not ready after 180 s`);
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  } finally {
    await context.dispose();
  }
}
