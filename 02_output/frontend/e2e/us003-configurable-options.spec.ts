import { expect, test } from '@playwright/test';
import { ui } from './support/contract';
import { formConfig, RegistrationPage } from './support/registration-page';

// US-003 Configurable conference options, as the participant sees them.

test('AC-003-01 active options are offered grouped by category with their names', async ({
  page,
}) => {
  const config = await formConfig(page.request);
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('EXTERNAL');

  for (const group of ui.options.groups) {
    const names = config.options.filter((o) => o.category === group.category).map((o) => o.name);
    expect(names.length, `configured ${group.category} options`).toBeGreaterThan(0);
    const fieldset = form.group(group.legend);
    await expect(fieldset).toBeVisible();
    for (const name of names) {
      await expect(fieldset.getByRole('checkbox', { name, exact: true })).toBeVisible();
      await expect(fieldset.getByRole('checkbox', { name, exact: true })).not.toBeChecked();
    }
  }
});

test('AC-003-02 an inactive option is not offered', async ({ page }) => {
  const form = new RegistrationPage(page);
  await form.open();
  await form.chooseType('STUDENT');
  await expect(form.group('Workshops')).toBeVisible();

  await expect(page.getByText('Delavnica: arhivirana tema')).toHaveCount(0);
});
