import { expect, test } from '@playwright/test';
import { organizerPassword, organizerUser } from './support/env';

test.describe('US-008 organizer export', () => {
  test('AC-008-06 organizer page offers the Excel download link', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Organizer export' }).click();
    await expect(page.getByRole('heading', { name: 'Organizer export' })).toBeVisible();
    const link = page.getByRole('link', { name: 'Download Excel export' });
    await expect(link).toHaveAttribute('href', '/api/organizer/export.xlsx');
  });

  test('AC-008-02 AC-008-06 download requires organizer credentials', async ({
    playwright,
    baseURL,
  }) => {
    const anonymous = await playwright.request.newContext({ baseURL });
    const denied = await anonymous.get('/api/organizer/export.xlsx');
    expect(denied.status()).toBe(401);
    expect(denied.headers()['www-authenticate']).toMatch(/^Basic/);
    expect(denied.headers()['content-type'] ?? '').not.toContain('spreadsheet');

    const wrong = await playwright.request.newContext({
      baseURL,
      httpCredentials: { username: organizerUser, password: 'wrong-password' },
    });
    expect((await wrong.get('/api/organizer/export.xlsx')).status()).toBe(401);

    const organizer = await playwright.request.newContext({
      baseURL,
      httpCredentials: { username: organizerUser, password: organizerPassword() },
    });
    const ok = await organizer.get('/api/organizer/export.xlsx');
    expect(ok.status()).toBe(200);
    expect(ok.headers()['content-type']).toContain(
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    );
    const body = await ok.body();
    expect(body.subarray(0, 2).toString('latin1')).toBe('PK');
    await Promise.all([anonymous.dispose(), wrong.dispose(), organizer.dispose()]);
  });
});
