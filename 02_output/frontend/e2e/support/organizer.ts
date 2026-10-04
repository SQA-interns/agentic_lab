import type { APIRequestContext } from '@playwright/test';

// Organizer export through the public API (token, then workbook download). The credentials
// come from the environment of the end-to-end run (verify.sh passes them via secrets.sh).
export async function exportWorkbook(api: APIRequestContext): Promise<Buffer> {
  const username = process.env.ORGANIZER_USERNAME;
  const password = process.env.ORGANIZER_PASSWORD;
  if (!username || !password) {
    throw new Error('ORGANIZER_USERNAME and ORGANIZER_PASSWORD must be set for the export');
  }
  const token = await api.post('/api/organizer/token', { data: { username, password } });
  if (token.status() !== 200) {
    throw new Error(`organizer token: status ${token.status()}`);
  }
  const { token: bearer } = (await token.json()) as { token: string };
  const workbook = await api.get('/api/organizer/registrations/export', {
    headers: { Authorization: `Bearer ${bearer}` },
  });
  if (workbook.status() !== 200) {
    throw new Error(`export: status ${workbook.status()}`);
  }
  return workbook.body();
}
