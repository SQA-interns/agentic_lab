import { expect, test, type Request } from '@playwright/test';
import { uniqueEmail } from './support/env';
import {
  confirmCaptchaAndConsent,
  expectFieldError,
  fillExternal,
  registrationIdShown,
} from './support/forms';

const values = () => ({
  firstName: 'Mojca',
  lastName: 'Zupan',
  email: uniqueEmail('e2e-confirm'),
  organization: 'Synthetic Org',
});

test.describe('US-004 confirmation only after backend acceptance', () => {
  test('AC-004-01 confirmation appears only after the acceptance response', async ({ page }) => {
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.route('**/api/registrations/external', async (route) => {
      await gate;
      await route.continue();
    });
    await page.goto('/register/external');
    await fillExternal(page, values());
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();

    await page.waitForTimeout(1500);
    await expect(page.getByRole('heading', { name: 'Registration received' })).toHaveCount(0);
    release();
    await registrationIdShown(page);
  });

  test('AC-004-02 server failure shows no confirmation, keeps data, and retry reuses the request ID', async ({
    page,
  }) => {
    const posted: Request[] = [];
    let fail = true;
    await page.route('**/api/registrations/external', async (route) => {
      posted.push(route.request());
      if (fail) {
        await route.fulfill({
          status: 503,
          contentType: 'application/problem+json',
          body: JSON.stringify({ type: 'about:blank', title: 'Service unavailable', status: 503 }),
        });
      } else {
        await route.continue();
      }
    });
    const v = values();
    await page.goto('/register/external');
    await fillExternal(page, v);
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();

    await expect(
      page.getByRole('alert').filter({ hasText: 'Your registration was not saved' }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Registration received' })).toHaveCount(0);
    await expect(page.getByLabel('First name')).toHaveValue(v.firstName);
    await expect(page.getByLabel('Email')).toHaveValue(v.email);

    fail = false;
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await registrationIdShown(page);
    expect(posted).toHaveLength(2);
    const ids = posted.map(
      (r) => (r.postDataJSON() as { clientRequestId: string }).clientRequestId,
    );
    expect(ids[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(ids[1]).toBe(ids[0]);
  });

  test('AC-004-02 network error shows no confirmation', async ({ page }) => {
    await page.route('**/api/registrations/external', (route) => route.abort('connectionrefused'));
    await page.goto('/register/external');
    await fillExternal(page, values());
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(
      page.getByRole('alert').filter({ hasText: 'Your registration was not saved' }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Registration received' })).toHaveCount(0);
  });

  test('AC-004-02 backend field errors are shown next to their fields', async ({ page }) => {
    const v = values();
    await page.goto('/register/external');
    await fillExternal(page, { ...v, email: 'someone@localhost' });
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();

    await expectFieldError(page, 'Email');
    await expect(page.getByRole('heading', { name: 'Registration received' })).toHaveCount(0);
    await expect(page.getByLabel('First name')).toHaveValue(v.firstName);
  });
});
