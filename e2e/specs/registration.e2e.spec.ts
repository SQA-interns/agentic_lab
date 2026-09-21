/**
 * End-to-end tests (specification § 14, US-001, US-002, US-004).
 *
 * Level justification: every other suite stops at a boundary — the API, the DOM, or a
 * mocked transport. These tests are the only ones that run the real browser against the
 * real backend, so they are what actually demonstrates AC-004-01 ("confirmation only
 * after a success response") and the responsive criteria of AC-G-12, which cannot be
 * observed without layout.
 */
import { expect, test, type Page } from '@playwright/test';

const EXTERNAL_FIELDS = {
  firstName: 'Špela',
  lastName: 'Čenčič',
  email: 'spela.cencic@example.org',
  organization: 'Univerza v Mariboru',
};

const STUDENT_FIELDS = {
  firstName: 'Žan',
  lastName: 'Šuštaršič',
  email: 'zan@student.example.org',
  studyInstitution: 'Univerza v Mariboru',
  studyProgramme: 'Računalništvo in informacijske tehnologije',
  studentId: 'F1234567',
};

async function fillFields(page: Page, values: Record<string, string>): Promise<void> {
  for (const [field, value] of Object.entries(values)) {
    await page.fill(`#field-${field}`, value);
  }
}

test.describe('external participant registration', () => {
  test('the form shows the fixed fields and the configured options', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#registration-form')).toBeVisible();

    for (const field of Object.keys(EXTERNAL_FIELDS)) {
      await expect(page.locator(`#field-${field}`)).toBeVisible();
      await expect(page.locator(`label[for="field-${field}"]`)).toBeVisible();
    }
    await expect(page.locator('#field-studentId')).toHaveCount(0);
    await expect(page.locator('#option-workshop-ai')).toBeVisible();
    // An inactive option is never offered (AC-003-04).
    await expect(page.locator('#option-other-poster-session')).toHaveCount(0);
  });

  test('the mandatory consent is not preselected (AC-G-08)', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#consent-privacy')).not.toBeChecked();
  });

  test('a complete submission shows the confirmation with the reference and the email (AC-004-01..03)', async ({
    page,
  }) => {
    await page.goto('/');
    await fillFields(page, EXTERNAL_FIELDS);
    await page.check('#option-workshop-ai');
    await page.check('#option-meal-lunch-day-1');
    await page.check('#consent-privacy');

    const response = page.waitForResponse(
      (res) => res.url().includes('/api/registrations') && res.request().method() === 'POST',
    );
    await page.click('#submit-button');
    expect((await response).status()).toBe(201);

    const confirmation = page.locator('#confirmation');
    await expect(confirmation).toBeVisible();
    await expect(confirmation).toContainText('Your registration has been received');
    await expect(confirmation).toContainText(/REG-\d{8}-[0-9A-Z]{10}/u);
    await expect(confirmation).toContainText(EXTERNAL_FIELDS.email);
    await expect(confirmation).toContainText('Workshop: Introduction to artificial intelligence');
    await expect(page.locator('#registration-form')).toBeHidden();
  });

  test('an invalid submission is refused in the browser and shows no confirmation (AC-004-04)', async ({ page }) => {
    await page.goto('/');
    await fillFields(page, { ...EXTERNAL_FIELDS, email: 'not-an-email' });
    await page.check('#consent-privacy');
    await page.click('#submit-button');

    await expect(page.locator('#field-email-error')).toContainText('valid email');
    await expect(page.locator('#field-email')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#confirmation')).toBeHidden();
  });

  test('submitting with nothing filled in shows errors and sends no request', async ({ page }) => {
    await page.goto('/');
    let posted = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/registrations') && req.method() === 'POST') {
        posted = true;
      }
    });
    await page.click('#submit-button');

    await expect(page.locator('#form-summary')).toBeVisible();
    await expect(page.locator('#field-firstName-error')).toContainText('required');
    expect(posted).toBe(false);
  });

  test('a backend failure shows a technical error, keeps the data and re-enables submitting (AC-004-05)', async ({
    page,
  }) => {
    await page.goto('/');
    await fillFields(page, EXTERNAL_FIELDS);
    await page.check('#consent-privacy');

    await page.route('**/api/registrations', (route) => route.abort('failed'));
    await page.click('#submit-button');

    await expect(page.locator('#form-summary')).toContainText('could not be sent');
    await expect(page.locator('#confirmation')).toBeHidden();
    await expect(page.locator('#registration-form')).toBeVisible();
    await expect(page.locator('#field-firstName')).toHaveValue(EXTERNAL_FIELDS.firstName);
    await expect(page.locator('#submit-button')).toBeEnabled();
  });

  test('the form is operable with the keyboard alone (AC-G-13)', async ({ page }) => {
    await page.goto('/');
    await page.locator('#field-firstName').focus();
    await page.keyboard.type(EXTERNAL_FIELDS.firstName);
    await page.keyboard.press('Tab');
    await page.keyboard.type(EXTERNAL_FIELDS.lastName);

    await expect(page.locator('#field-lastName')).toHaveValue(EXTERNAL_FIELDS.lastName);
  });
});

test.describe('student registration', () => {
  test('the student form has its own fields and hides externals-only options', async ({ page }) => {
    await page.goto('/studentska-prijava/');
    await expect(page.locator('#registration-form')).toBeVisible();

    for (const field of Object.keys(STUDENT_FIELDS)) {
      await expect(page.locator(`#field-${field}`)).toBeVisible();
    }
    await expect(page.locator('#field-organization')).toHaveCount(0);
    await expect(page.locator('#option-workshop-industry-round-table')).toHaveCount(0);
    await expect(page.locator('#option-meal-conference-dinner')).toHaveCount(0);
  });

  test('a complete student submission is confirmed (AC-002-03)', async ({ page }) => {
    await page.goto('/studentska-prijava/');
    await fillFields(page, STUDENT_FIELDS);
    await page.check('#option-workshop-ai');
    await page.check('#consent-privacy');
    await page.click('#submit-button');

    await expect(page.locator('#confirmation')).toContainText('Your registration has been received');
    await expect(page.locator('#confirmation')).toContainText(STUDENT_FIELDS.email);
  });

  test('each form links to the other', async ({ page }) => {
    await page.goto('/');
    await page.click('nav.variant-nav a');
    await expect(page).toHaveURL(/studentska-prijava/u);
    await page.click('nav.variant-nav a');
    await expect(page.locator('#field-organization')).toBeVisible();
  });
});

test.describe('responsive layout (AC-G-12)', () => {
  for (const [label, url] of [
    ['external', '/'],
    ['student', '/studentska-prijava/'],
  ] as const) {
    test(`the ${label} form has no horizontal overflow at the current viewport`, async ({ page }) => {
      await page.goto(url);
      await expect(page.locator('#registration-form')).toBeVisible();

      const overflow = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }));
      expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth + 1);
    });
  }

  test('all controls stay reachable and large enough to tap', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#field-firstName')).toBeVisible();
    await expect(page.locator('#submit-button')).toBeVisible();

    const height = await page.locator('#submit-button').evaluate((node) => node.getBoundingClientRect().height);
    expect(height).toBeGreaterThanOrEqual(40);
  });

  test('the confirmation is readable without horizontal scrolling', async ({ page }) => {
    await page.goto('/');
    await fillFields(page, EXTERNAL_FIELDS);
    await page.check('#consent-privacy');
    await page.click('#submit-button');
    await expect(page.locator('#confirmation')).toBeVisible();

    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));
    expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth + 1);
  });
});
