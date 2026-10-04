import { expect, test } from '@playwright/test';
import { ui, uniqueEmail } from './support/contract';
import { formConfig, RegistrationPage } from './support/registration-page';

// US-004 Registration confirmation in the application.

test('AC-004-01 after acceptance the confirmation replaces the form', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('STUDENT');
  await form.fill('STUDENT', {
    firstName: 'Tina',
    lastName: 'Kos',
    email: uniqueEmail('e2e.tina'),
    studyInstitution: 'Univerza na Primorskem',
    studyProgramme: 'Matematika',
    studentId: '89200001',
  });
  const config = await formConfig(page.request);
  await form.consent(config.consent.text).check();
  await form.passCaptcha();
  await form.submit();

  await expect(form.confirmation()).toContainText(ui.confirmation.heading);
  await expect(form.confirmation()).toContainText('Tina');
  await expect(page.getByRole('button', { name: ui.submit.name, exact: true })).toHaveCount(0);
});

test('AC-004-02 a rejected submission shows the reasons and no confirmation', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', {
    firstName: 'Ana',
    lastName: '',
    email: 'ana@',
    organization: 'Univerza v Mariboru',
  });
  await form.submit();

  await form.expectFieldError('EXTERNAL', 'lastName', 'REQUIRED');
  await form.expectFieldError('EXTERNAL', 'email', 'INVALID_EMAIL');
  await expect(page.getByText(ui.errors.messages.CONSENT_REQUIRED, { exact: true })).toBeVisible();
  await expect(form.confirmation()).toHaveCount(0);
  await expect(page.getByRole('button', { name: ui.submit.name, exact: true })).toBeVisible();
});
