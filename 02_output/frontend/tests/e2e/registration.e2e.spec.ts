import { expect, test, type Page } from '@playwright/test';
import { attachment, awaitMail, setting, uniqueEmail, unzipText } from './support';

const EXPORT = '/api/admin/registrations/export';
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/;

function organizerAuth(): string {
  const user = setting('ORGANIZER_USERNAME');
  const password = setting('ORGANIZER_PASSWORD');
  return 'Basic ' + Buffer.from(`${user}:${password}`).toString('base64');
}

function organizerEmails(): string[] {
  return setting('ORGANIZER_EMAILS')
    .split(',')
    .map((e) => e.trim())
    .filter(Boolean);
}

async function chooseType(page: Page, type: 'External participant' | 'Student') {
  await page.getByRole('radio', { name: type }).check();
}

async function acceptAndSubmit(page: Page): Promise<string> {
  await page.getByRole('group', { name: 'Workshops' }).getByRole('checkbox').first().check();
  await page.getByRole('group', { name: 'Meals' }).getByRole('checkbox').first().check();
  const consents = page.getByRole('checkbox', { name: /^I agree that the organizers/ });
  await consents.first().check();
  await page.getByRole('checkbox', { name: /I am not a robot \(test mode\)/ }).check();
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('heading', { name: /Registration received/ })).toBeVisible();
  const text = await page.locator('body').innerText();
  const reference = UUID.exec(text)?.[0];
  expect(reference, 'reference shown in the confirmation').toBeTruthy();
  return reference as string;
}

test('NFR-01 AC-001-01 AC-006-01 AC-007-01 AC-008-03 Slovenian characters survive form, emails and export', async ({
  page,
  request,
}) => {
  const email = uniqueEmail();
  await page.goto('/');
  await chooseType(page, 'External participant');
  await page.getByLabel(/^First name/).fill('Špela Žužek');
  await page.getByLabel(/^Last name/).fill('Čučnik-Šraj');
  await page.getByLabel(/^Email/).fill(email);
  await page.getByLabel(/^Organization/).fill('Zavod Škofja Loka, Črnomelj');

  const reference = await acceptAndSubmit(page);

  const participantMail = await awaitMail(email, reference);
  expect(participantMail.Text).toContain('Špela Žužek');
  expect(participantMail.Text).toContain('Čučnik-Šraj');

  for (const organizer of organizerEmails()) {
    const organizerMail = await awaitMail(organizer, reference);
    expect(organizerMail.Text).toContain('Zavod Škofja Loka, Črnomelj');
    const file = organizerMail.Attachments.find(
      (a) => a.FileName === `registration-${reference}.json`,
    );
    expect(file, 'JSON attachment').toBeTruthy();
    const json = JSON.parse(await attachment(organizerMail.ID, file!.PartID));
    expect(json.participant.firstName).toBe('Špela Žužek');
    expect(json.participant.lastName).toBe('Čučnik-Šraj');
  }

  const exported = await request.get(EXPORT, { headers: { Authorization: organizerAuth() } });
  expect(exported.status()).toBe(200);
  const strings = unzipText(
    Buffer.from(await exported.body()),
    /^xl\/(sharedStrings|worksheets\/sheet\d+)\.xml$/,
  );
  expect(strings).toContain('Čučnik-Šraj');
  expect(strings).toContain('Zavod Škofja Loka, Črnomelj');
  expect(strings).toContain(reference);
});

test('AC-002-01 AC-004-01 a student registers through the form', async ({ page }) => {
  const email = uniqueEmail();
  await page.goto('/');
  await chooseType(page, 'Student');
  await page.getByLabel(/^First name/).fill('Luka');
  await page.getByLabel(/^Last name/).fill('Kovač');
  await page.getByLabel(/^Email/).fill(email);
  await page.getByLabel(/^Study institution/).fill('Univerza v Mariboru');
  await page.getByLabel(/^Study programme/).fill('Informatika in tehnologije komuniciranja');
  await page.getByLabel(/^Student ID/).fill('93100042');

  const reference = await acceptAndSubmit(page);

  const mail = await awaitMail(email, reference);
  expect(mail.Text).toContain('Kovač');
});

test('AC-008-01 AC-008-02 DoD-P04 the organizer gets a workbook; without credentials the same request is refused', async ({
  request,
}) => {
  const allowed = await request.get(EXPORT, { headers: { Authorization: organizerAuth() } });
  expect(allowed.status()).toBe(200);
  expect(allowed.headers()['content-type']).toContain(
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  );
  const workbook = unzipText(Buffer.from(await allowed.body()), /^xl\/workbook\.xml$/);
  expect(workbook).toContain('Registrations');

  const refused = await request.get(EXPORT);
  expect(refused.status()).toBe(401);
  expect(refused.headers()['content-type'] ?? '').not.toContain('spreadsheetml');
});
