import { expect, test } from '@playwright/test';
import { uniqueEmail } from './support/env';
import {
  CAPTCHA_LABEL,
  confirmCaptchaAndConsent,
  consentCheckbox,
  expectFieldError,
  fillExternal,
  registrationIdShown,
  tabTo,
} from './support/forms';
import { awaitMessage } from './support/mailpit';

// Synthetic local catalog: 02_output/config/conference.local.json
const ACTIVE = {
  Workshops: ['Workshop A – Testing', 'Workshop B – Secure coding'],
  Events: ['Welcome reception', 'Ljubljana city tour'],
  Meals: ['Lunch', 'Conference dinner'],
  'Other activities': ['Poster session', 'Mentoring slot'],
};
const INACTIVE = [
  'Workshop C – Retired topic',
  'Cancelled boat trip',
  'Breakfast (not offered)',
  'Closed hackathon',
];

test.describe('US-001 external participant form', () => {
  test('AC-001-09 AC-003-01 form shows labelled fields, active grouped options and unchecked consent', async ({
    page,
  }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'External participant registration' }).click();
    await expect(
      page.getByRole('heading', { name: 'External participant registration' }),
    ).toBeVisible();

    for (const label of ['First name', 'Last name', 'Email', 'Organization / institution']) {
      await expect(page.getByLabel(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByLabel('Student ID')).toHaveCount(0);
    for (const [legend, names] of Object.entries(ACTIVE)) {
      const group = page.getByRole('group', { name: legend });
      await expect(group).toBeVisible();
      for (const name of names) {
        await expect(group.getByRole('checkbox', { name, exact: true })).not.toBeChecked();
      }
    }
    for (const name of INACTIVE) {
      await expect(page.getByText(name)).toHaveCount(0);
    }
    await expect(await consentCheckbox(page)).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: CAPTCHA_LABEL })).not.toBeChecked();
  });

  test('AC-001-09 AC-001-04 AC-004-01 keyboard-only submission with Unicode values reaches confirmation', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail('e2e-keyboard');
    await page.goto('/register/external');
    await expect(page.getByLabel('First name')).toBeVisible();

    const typeInto = async (label: string, text: string) => {
      const field = page.getByLabel(label, { exact: true });
      await tabTo(page, field);
      const outline = await field.evaluate((el) => {
        const style = getComputedStyle(el);
        return `${style.outlineStyle}|${style.outlineWidth}|${style.boxShadow}`;
      });
      expect(outline, `visible focus on ${label}`).not.toMatch(/^none\|.*\|none$/);
      await page.keyboard.type(text);
    };
    await typeInto('First name', ' Žiga Čedomir ');
    await typeInto('Last name', 'Šuštaršič');
    await typeInto('Email', email);
    await typeInto('Organization / institution', 'Inštitut za čebelarstvo');

    const workshop = page.getByRole('checkbox', { name: 'Workshop A – Testing', exact: true });
    await tabTo(page, workshop);
    await page.keyboard.press('Space');
    await expect(workshop).toBeChecked();
    const consent = await consentCheckbox(page);
    await tabTo(page, consent);
    await page.keyboard.press('Space');
    const captcha = page.getByRole('checkbox', { name: CAPTCHA_LABEL });
    await tabTo(page, captcha);
    await page.keyboard.press('Space');
    const submit = page.getByRole('button', { name: 'Submit registration' });
    await tabTo(page, submit);
    await page.keyboard.press('Enter');

    const id = await registrationIdShown(page);
    const mail = await awaitMessage(request, email);
    expect(mail.Text).toContain(id);
    expect(mail.Text).toContain('Žiga Čedomir');
    expect(mail.Text).toContain('Šuštaršič');
    expect(mail.Text).not.toContain(' Žiga');
  });

  test('AC-001-09 empty submission shows associated field errors and an error summary', async ({
    page,
  }) => {
    await page.goto('/register/external');
    await page.getByRole('button', { name: 'Submit registration' }).click();

    const summary = page.getByRole('alert').filter({ hasText: 'Please correct the following' });
    await expect(summary).toBeVisible();
    await expect(summary).toBeFocused();
    for (const label of ['First name', 'Last name', 'Email', 'Organization / institution']) {
      await expectFieldError(page, label);
    }
    await expect(page.getByRole('heading', { name: 'Registration received' })).toHaveCount(0);
  });

  test('AC-001-09 AC-001-03 invalid email and missing consent are reported before sending', async ({
    page,
  }) => {
    await page.goto('/register/external');
    await fillExternal(page, {
      firstName: 'Ana',
      lastName: 'Novak',
      email: 'not-an-email',
      organization: 'Synthetic Org',
    });
    await page.getByRole('checkbox', { name: CAPTCHA_LABEL }).check();
    let posted = false;
    page.on('request', (r) => {
      if (r.method() === 'POST' && r.url().includes('/api/registrations')) posted = true;
    });
    await page.getByRole('button', { name: 'Submit registration' }).click();

    await expectFieldError(page, 'Email');
    await expect(
      page.getByRole('alert').filter({ hasText: 'Please correct the following' }),
    ).toContainText('consent', { ignoreCase: true });
    expect(posted).toBe(false);
  });

  test('AC-001-01 AC-001-07 valid external registration through the UI is accepted', async ({
    page,
  }) => {
    await page.goto('/register/external');
    await fillExternal(page, {
      firstName: 'Ana',
      lastName: 'Novak',
      email: uniqueEmail('e2e-external'),
      organization: 'Synthetic Org',
    });
    await page.getByRole('checkbox', { name: 'Lunch', exact: true }).check();
    await page.getByRole('checkbox', { name: 'Poster session', exact: true }).check();
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await registrationIdShown(page);
  });
});
