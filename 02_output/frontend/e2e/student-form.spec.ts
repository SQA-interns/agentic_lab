import { expect, test } from '@playwright/test';
import { uniqueEmail } from './support/env';
import {
  CAPTCHA_LABEL,
  confirmCaptchaAndConsent,
  consentCheckbox,
  expectFieldError,
  fillStudent,
  registrationIdShown,
  tabTo,
} from './support/forms';
import { awaitMessage } from './support/mailpit';

test.describe('US-002 student form', () => {
  test('AC-002-08 student form shows study fields, grouped options and unchecked consent', async ({
    page,
  }) => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Student registration' }).click();
    await expect(page.getByRole('heading', { name: 'Student registration' })).toBeVisible();
    for (const label of [
      'First name',
      'Last name',
      'Email',
      'Study institution',
      'Study programme',
      'Student ID',
    ]) {
      await expect(page.getByLabel(label, { exact: true })).toBeVisible();
    }
    await expect(page.getByLabel('Organization / institution')).toHaveCount(0);
    for (const legend of ['Workshops', 'Events', 'Meals', 'Other activities']) {
      await expect(page.getByRole('group', { name: legend })).toBeVisible();
    }
    await expect(await consentCheckbox(page)).not.toBeChecked();
  });

  test('AC-002-08 empty student submission shows every required field error', async ({ page }) => {
    await page.goto('/register/student');
    const submit = page.getByRole('button', { name: 'Submit registration' });
    await tabTo(page, submit);
    await page.keyboard.press('Enter');
    for (const label of [
      'First name',
      'Last name',
      'Email',
      'Study institution',
      'Study programme',
      'Student ID',
    ]) {
      await expectFieldError(page, label);
    }
    await expect(
      page.getByRole('alert').filter({ hasText: 'Please correct the following' }),
    ).toBeFocused();
  });

  test('AC-002-01 AC-002-04 AC-004-01 student registration with Unicode study details is accepted', async ({
    page,
    request,
  }) => {
    const email = uniqueEmail('e2e-student');
    await page.goto('/register/student');
    await fillStudent(page, {
      firstName: ' Nuša ',
      lastName: 'Žagar',
      email,
      studyInstitution: 'Fakulteta za računalništvo in informatiko',
      studyProgramme: 'Računalništvo – UNI',
      studentId: '6320ŽČ/1',
    });
    await page.getByRole('checkbox', { name: 'Workshop B – Secure coding', exact: true }).check();
    await page.getByRole('checkbox', { name: 'Welcome reception', exact: true }).check();
    await confirmCaptchaAndConsent(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();

    const id = await registrationIdShown(page);
    const mail = await awaitMessage(request, email);
    expect(mail.Text).toContain(id);
    expect(mail.Text).toContain('Nuša');
    expect(mail.Text).toContain('Workshop B – Secure coding');
  });

  test('AC-002-06 unchecked stub captcha is reported and nothing is sent', async ({ page }) => {
    await page.goto('/register/student');
    await fillStudent(page, {
      firstName: 'Tina',
      lastName: 'Kranjc',
      email: uniqueEmail('e2e-captcha'),
      studyInstitution: 'Synthetic University',
      studyProgramme: 'Physics',
      studentId: 'X1',
    });
    await (await consentCheckbox(page)).check();
    let posted = false;
    page.on('request', (r) => {
      if (r.method() === 'POST' && r.url().includes('/api/registrations')) posted = true;
    });
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(
      page.getByRole('alert').filter({ hasText: 'Please correct the following' }),
    ).toContainText('captcha', { ignoreCase: true });
    await expect(page.getByRole('checkbox', { name: CAPTCHA_LABEL })).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(posted).toBe(false);
  });
});
