import { expect, test, type Locator } from '@playwright/test';
import { ui, uniqueEmail } from './support/contract';
import { formConfig, RegistrationPage } from './support/registration-page';

// US-002 Student registration, end to end against the local stack.

test('AC-002-01 a student registers with options and sees the confirmation', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('STUDENT');
  await form.fill('STUDENT', {
    firstName: 'Luka',
    lastName: 'Kranjc',
    email: uniqueEmail('e2e.luka'),
    studyInstitution: 'Univerza v Ljubljani',
    studyProgramme: 'Računalništvo in informatika',
    studentId: '63210042',
  });
  await form.option('Delavnica: varnost spletnih aplikacij').check();
  await form.option('Sprejem dobrodošlice').check();
  const config = await formConfig(page.request);
  await form.consent(config.consent.text).check();
  await form.passCaptcha();
  await form.submit();

  await expect(form.confirmation()).toContainText(ui.confirmation.heading);
});

test('AC-002-02 the student form asks for exactly the student fields', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('STUDENT');

  for (const field of ui.forms.STUDENT) {
    await expect(page.getByLabel(field.label, { exact: true })).toBeVisible();
  }
  await expect(page.getByLabel('Organization / institution', { exact: true })).toHaveCount(0);
});

test('AC-002-08 the student form offers the same active options as the external form', async ({
  page,
}) => {
  const form = new RegistrationPage(page);
  const config = await formConfig(page.request);
  const offered: Record<string, string[]> = {};
  await form.open();
  for (const type of ['EXTERNAL', 'STUDENT'] as const) {
    await form.chooseType(type);
    const names: string[] = [];
    for (const group of ui.options.groups) {
      const boxes = form.group(group.legend).getByRole('checkbox');
      const count = await boxes.count();
      for (let i = 0; i < count; i += 1) {
        names.push(await labelText(boxes.nth(i)));
      }
    }
    offered[type] = names;
  }

  expect(offered.STUDENT).toEqual(offered.EXTERNAL);
  expect(offered.STUDENT).toEqual(config.options.map((o) => o.name));
});

async function labelText(box: Locator): Promise<string> {
  return box.evaluate((el) => (el as HTMLInputElement).labels?.[0]?.textContent?.trim() ?? '');
}
