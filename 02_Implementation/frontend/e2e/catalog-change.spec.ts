import { expect, test } from '@playwright/test';
import { sql, uniqueEmail } from './stack';

// Runs only while the backend was restarted with config/fixtures/catalog-changed.yaml
// (tools/m3-catalog-restart.sh). Tag keeps it out of the default e2e run.
test.describe('@catalog-changed P-05/P-06 changed catalog and synthetic consent', () => {
  test('new catalog is displayed; fixed fields unchanged; consent starts unchecked', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Example Conference 2027');
    await expect(page.getByLabel('Harbour cruise')).toBeVisible();
    await expect(page.getByLabel('Workshop: AI safety fundamentals')).toBeVisible();
    await expect(page.getByLabel('Workshop: Contributing to open source')).toHaveCount(0);
    await expect(page.getByLabel('Guided city tour')).toHaveCount(0);
    for (const label of ['First name', 'Last name', 'Email', 'Organization / institution']) {
      await expect(page.getByLabel(label)).toBeVisible();
    }
    await expect(page.getByLabel(/SYNTHETIC TEST FIXTURE/)).not.toBeChecked();
  });

  test('external: consent absent blocked, present accepted', async ({ page }) => {
    await page.goto('/');
    await page.getByLabel('First name').fill('Tina');
    await page.getByLabel('Last name').fill('Konfiguracija');
    await page.getByLabel('Email').fill(uniqueEmail('tina.m3'));
    await page.getByLabel('Organization / institution').fill('Društvo');
    await page.getByLabel('Workshop: AI safety fundamentals').check();
    await page.getByLabel('I am not a robot (local test captcha)').check();
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(page.getByText('This consent is required.')).toBeVisible();
    await expect(page.getByRole('status')).toHaveCount(0);
    await page.getByLabel(/SYNTHETIC TEST FIXTURE/).check();
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(page.getByRole('status')).toContainText('Registration received');
    const id = (await page.getByTestId('registration-id').textContent()) ?? '';
    const rows = sql(
      `SELECT c.consent_id, s.option_id FROM registration_consent c JOIN registration_selection s ` +
        `ON s.registration_id = c.registration_id WHERE c.registration_id = '${id}'`,
    );
    expect(rows).toEqual([['synthetic-required-consent', 'ws-ai-safety']]);
  });

  test('student form works with the new catalog', async ({ page }) => {
    await page.goto('/#student');
    await page.getByLabel('First name').fill('Miha');
    await page.getByLabel('Last name').fill('Študent');
    await page.getByLabel('Email').fill(uniqueEmail('miha.m3'));
    await page.getByLabel('Study institution').fill('Univerza v Novi Gorici');
    await page.getByLabel('Study programme').fill('Fizika');
    await page.getByLabel('Student ID').fill('M3-0001');
    await page.getByLabel('Vegan dinner').check();
    await page.getByLabel(/SYNTHETIC TEST FIXTURE/).check();
    await page.getByLabel('I am not a robot (local test captcha)').check();
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(page.getByRole('status')).toContainText('Registration received');
  });

  test('backend enforces consent and retired options independently of the UI', async ({
    request,
  }) => {
    const base = {
      firstName: 'Api',
      lastName: 'Probe',
      email: uniqueEmail('api.m3'),
      organization: 'Org',
      captchaToken: 'local-test-captcha-pass',
    };
    const empty = { workshops: [], events: [], meals: [], otherActivities: [] };
    const post = (body: object) => request.post('/api/registrations/external', { data: body });

    const noConsent = await post({
      ...base,
      clientRequestId: crypto.randomUUID(),
      selections: empty,
    });
    expect(noConsent.status()).toBe(400);
    expect((await noConsent.json()).code).toBe('CONSENT_REQUIRED');

    const retired = await post({
      ...base,
      clientRequestId: crypto.randomUUID(),
      selections: { ...empty, workshops: ['ws-open-source'] },
      consents: { 'synthetic-required-consent': true },
    });
    expect(retired.status()).toBe(400);
    expect((await retired.json()).code).toBe('OPTION_INVALID');

    const ok = await post({
      ...base,
      clientRequestId: crypto.randomUUID(),
      selections: { ...empty, events: ['ev-harbour-cruise'] },
      consents: { 'synthetic-required-consent': true },
    });
    expect(ok.status()).toBe(201);
  });
});
