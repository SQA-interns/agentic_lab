import { expect, test } from '@playwright/test';
import { labelOf, ui, uniqueEmail } from './support/contract';
import { attachment, awaitMessage, mailpit } from './support/mailpit';
import { exportWorkbook } from './support/organizer';
import { formConfig, RegistrationPage } from './support/registration-page';
import { readFirstSheet } from './support/xlsx';

// US-001 External participant registration, end to end against the local stack.

test('AC-001-01 an external participant registers with options and sees the confirmation', async ({
  page,
}) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', {
    firstName: 'Ana',
    lastName: 'Novak',
    email: uniqueEmail('e2e.ana'),
    organization: 'Univerza v Mariboru',
  });
  await form.option('Delavnica: umetna inteligenca v praksi').check();
  await form.option('Kosilo, 1. dan').check();
  const config = await formConfig(page.request);
  await form.consent(config.consent.text).check();
  await form.passCaptcha();
  await form.submit();

  await expect(form.confirmation()).toContainText(ui.confirmation.heading);
});

test('AC-001-02 the external form asks for exactly the external fields', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');

  for (const field of ui.forms.EXTERNAL) {
    await expect(page.getByLabel(field.label, { exact: true })).toBeVisible();
  }
  for (const field of ui.forms.STUDENT.filter(
    (s) => !ui.forms.EXTERNAL.some((e) => e.field === s.field),
  )) {
    await expect(page.getByLabel(field.label, { exact: true })).toHaveCount(0);
  }
});

test('AC-001-03 empty required fields are marked and nothing is confirmed', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', { firstName: '   ' });
  await form.submit();

  for (const field of ['firstName', 'lastName', 'email', 'organization']) {
    await form.expectFieldError('EXTERNAL', field, 'REQUIRED');
  }
  await expect(form.confirmation()).toHaveCount(0);
});

test('AC-001-05 an invalid email is marked as invalid', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', {
    firstName: 'Ana',
    lastName: 'Novak',
    email: 'ana.novak@example',
    organization: 'Univerza v Mariboru',
  });
  await form.submit();

  await form.expectFieldError('EXTERNAL', 'email', 'INVALID_EMAIL');
  await expect(form.confirmation()).toHaveCount(0);
});

test('AC-001-06 Slovenian characters survive form, emails, JSON copy and export (NFR-01)', async ({
  page,
}) => {
  const email = uniqueEmail('e2e.spela');
  const firstName = 'Špela Ćiril';
  const lastName = 'Žagar-Čebašek';
  const organization = 'Inštitut Jožef Stefan – Odsek za računalništvo (ščž ŠČŽ)';
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', { firstName, lastName, email, organization });
  await form.option('Večerja za udeležence').check();
  const config = await formConfig(page.request);
  await form.consent(config.consent.text).check();
  await form.passCaptcha();
  await form.submit();
  await expect(form.confirmation()).toContainText(ui.confirmation.heading);

  const mail = await mailpit();
  const confirmation = await awaitMessage(mail, email);
  expect(confirmation.Text).toContain(firstName);
  expect(confirmation.Text).toContain(lastName);
  expect(confirmation.Text).toContain(organization);
  expect(confirmation.Text).toContain('Večerja za udeležence');

  const organizerAddress = (process.env.ORGANIZER_EMAILS ?? '').split(',')[0].trim();
  const notification = await awaitMessage(mail, organizerAddress, email);
  expect(notification.Text).toContain(lastName);
  const json = await attachment(mail, notification.ID, notification.Attachments[0].PartID);
  const copy = JSON.parse(json.toString('utf8')) as { participant: Record<string, string> };
  expect(copy.participant.firstName).toBe(firstName);
  expect(copy.participant.lastName).toBe(lastName);
  expect(copy.participant.organization).toBe(organization);

  const sheet = readFirstSheet(await exportWorkbook(page.request));
  const headings = sheet.rows[0];
  const row = sheet.rows.find((r) => r[headings.indexOf('Email')] === email);
  expect(row, 'export row').toBeDefined();
  expect(row?.[headings.indexOf('First name')]).toBe(firstName);
  expect(row?.[headings.indexOf('Last name')]).toBe(lastName);
  expect(row?.[headings.indexOf('Organization / institution')]).toBe(organization);
});

test('AC-001-11 the consent shows the configured wording and is not selected', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  const config = await formConfig(page.request);

  await expect(form.consent(config.consent.text)).toBeVisible();
  await expect(form.consent(config.consent.text)).not.toBeChecked();
});

test('AC-001-12 submitting without the consent marks it as required', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');
  await form.fill('EXTERNAL', {
    firstName: 'Ana',
    lastName: 'Novak',
    email: uniqueEmail('e2e.noconsent'),
    organization: 'Univerza v Mariboru',
  });
  await form.passCaptcha();
  await form.submit();

  await expect(page.getByText(ui.errors.messages.CONSENT_REQUIRED, { exact: true })).toBeVisible();
  await expect(form.confirmation()).toHaveCount(0);
});

test('AC-001-14 an already registered email is refused with a message', async ({ page }) => {
  const email = uniqueEmail('e2e.twice');
  const form = new RegistrationPage(page);
  const config = await formConfig(page.request);
  for (const attempt of [1, 2]) {
    await form.open();
    await form.chooseType('EXTERNAL');
    await form.fill('EXTERNAL', {
      firstName: 'Ana',
      lastName: 'Novak',
      email: attempt === 1 ? email : `  ${email.toUpperCase()} `,
      organization: 'Univerza v Mariboru',
    });
    await form.consent(config.consent.text).check();
    await form.passCaptcha();
    await form.submit();
    if (attempt === 1) {
      await expect(form.confirmation()).toContainText(ui.confirmation.heading);
    }
  }

  await expect(form.formError()).toContainText(ui.errors.messages.EMAIL_ALREADY_REGISTERED);
  await expect(form.confirmation()).toHaveCount(0);
  await expect(page.getByLabel(labelOf('EXTERNAL', 'email'), { exact: true })).toBeVisible();
});
