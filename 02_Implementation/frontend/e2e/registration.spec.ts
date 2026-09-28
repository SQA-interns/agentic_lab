import { expect, test, type Page } from '@playwright/test';
import { backupFile, sha256, sql, uniqueEmail } from './stack';

const NBSP = ' ';

async function completeCaptcha(page: Page) {
  await page.getByLabel('I am not a robot (local test captcha)').check();
}

/**
 * Holds the backend response until the UI state has been checked, proving the confirmation is
 * rendered only after backend acceptance (AC-001-06, US-004).
 */
async function submitAndAssertOrdering(page: Page): Promise<string> {
  let sawNoConfirmationBeforeResponse = false;
  await page.route('**/api/registrations/*', async (route) => {
    const response = await route.fetch();
    sawNoConfirmationBeforeResponse =
      (await page.getByRole('status').count()) === 0 &&
      (await page.getByRole('button', { name: 'Submitting…' }).isDisabled());
    await route.fulfill({ response });
  });
  await page.getByRole('button', { name: 'Submit registration' }).click();
  await expect(page.getByRole('status')).toContainText('Registration received');
  expect(sawNoConfirmationBeforeResponse).toBe(true);
  await page.unroute('**/api/registrations/*');
  const id = (await page.getByTestId('registration-id').textContent()) ?? '';
  expect(id).toMatch(/^[0-9a-f-]{36}$/);
  await expect(page.getByRole('status')).toContainText('queued');
  return id;
}

function assertStored(id: string, expectedFirstName: string) {
  const rows = sql(`SELECT first_name, raw_json_sha256 FROM registration WHERE id = '${id}'`);
  expect(rows).toHaveLength(1);
  const [firstName, dbSha] = rows[0] ?? [];
  expect(firstName).toBe(expectedFirstName);
  const file = backupFile(id);
  expect(sha256(file)).toBe(dbSha);
  expect(JSON.parse(file).participant.firstName).toBe(expectedFirstName);
  const outbox = sql(`SELECT kind FROM email_outbox WHERE registration_id = '${id}' ORDER BY kind`);
  expect(outbox.map((r) => r[0])).toEqual(
    expect.arrayContaining(['ORGANIZER_NOTIFICATION', 'PARTICIPANT_CONFIRMATION']),
  );
}

test.describe('registration journeys (real stack)', () => {
  test('M0: frontend loads configuration from backend', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('registration');
    await expect(page.getByRole('group', { name: 'Workshops' })).toBeVisible();
  });

  test('US-001: external participant registers with Unicode and NBSP', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'External participant' }).click();
    await page.getByLabel('First name').fill(`${NBSP}Špela${NBSP}`);
    await page.getByLabel('Last name').fill('Čebašek Žužek');
    await page.getByLabel('Email').fill(uniqueEmail('spela.e2e'));
    await page.getByLabel('Organization / institution').fill('Inštitut Ščit');
    await page.getByRole('group', { name: 'Workshops' }).getByRole('checkbox').first().check();
    await completeCaptcha(page);
    const id = await submitAndAssertOrdering(page);
    assertStored(id, 'Špela');
  });

  test('US-001: blank and malformed input is rejected before success', async ({ page }) => {
    await page.goto('/');
    await page.getByLabel('First name').fill(`${NBSP}${NBSP}`);
    await page.getByLabel('Email').fill('not-an-email');
    await completeCaptcha(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(page.getByRole('alert')).toContainText('correct the highlighted fields');
    await expect(page.getByText('This field is required.').first()).toBeVisible();
    await expect(page.getByText('Enter a valid email address.')).toBeVisible();
    await expect(page.getByRole('status')).toHaveCount(0);
  });

  test('US-002: student registers through the same stack', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'Student' }).click();
    await page.getByLabel('First name').fill('Luka');
    await page.getByLabel('Last name').fill('Študent');
    await page.getByLabel('Email').fill(uniqueEmail('luka.e2e'));
    await page.getByLabel('Study institution').fill('Univerza v Mariboru');
    await page.getByLabel('Study programme').fill('Računalništvo in informatika');
    await page.getByLabel('Student ID').fill('E2E-4242');
    await page.getByRole('group', { name: 'Meals' }).getByRole('checkbox').first().check();
    await completeCaptcha(page);
    const id = await submitAndAssertOrdering(page);
    assertStored(id, 'Luka');
    const [row] = sql(
      `SELECT participant_type, study_programme, student_id FROM registration WHERE id='${id}'`,
    );
    expect(row).toEqual(['STUDENT', 'Računalništvo in informatika', 'E2E-4242']);
  });

  test('US-002: student required fields are enforced in the UI', async ({ page }) => {
    await page.goto('/#student');
    await page.getByLabel('First name').fill('Luka');
    await page.getByLabel('Last name').fill('Študent');
    await page.getByLabel('Email').fill(uniqueEmail('luka.missing'));
    await completeCaptcha(page);
    await page.getByRole('button', { name: 'Submit registration' }).click();
    await expect(page.getByText('This field is required.')).toHaveCount(3);
    await expect(page.getByRole('status')).toHaveCount(0);
  });
});
