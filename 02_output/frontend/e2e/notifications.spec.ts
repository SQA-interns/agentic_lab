import { expect, test } from '@playwright/test';
import { uniqueEmail } from './support/env';
import { confirmCaptchaAndConsent, fillExternal, registrationIdShown } from './support/forms';
import { attachment, awaitMessage } from './support/mailpit';

test('AC-006-01 AC-007-01 UI registration produces participant and organizer mail with JSON attachment', async ({
  page,
  request,
}) => {
  const email = uniqueEmail('e2e-mail');
  await page.goto('/register/external');
  await fillExternal(page, {
    firstName: 'Luka',
    lastName: 'Horvat',
    email,
    organization: 'Synthetic Org',
  });
  await page.getByRole('checkbox', { name: 'Conference dinner', exact: true }).check();
  await confirmCaptchaAndConsent(page);
  await page.getByRole('button', { name: 'Submit registration' }).click();
  const id = await registrationIdShown(page);

  const participant = await awaitMessage(request, email);
  expect(participant.To.map((t) => t.Address)).toEqual([email]);
  expect(participant.Text).toContain(id);
  expect(participant.Text).toContain('Conference dinner');

  const organizer = await awaitMessage(request, 'organizer@example.test', id);
  expect(organizer.Text).toContain(email);
  expect(organizer.Text).toContain('Luka');
  expect(organizer.Attachments).toHaveLength(1);
  const [file] = organizer.Attachments;
  expect(file.FileName).toBe(`registration-${id}.json`);
  const record = JSON.parse(
    (await attachment(request, organizer.ID, file.PartID)).toString('utf8'),
  ) as { registrationId: string; participant: { email: string } };
  expect(record.registrationId).toBe(id);
  expect(record.participant.email).toBe(email);
});
