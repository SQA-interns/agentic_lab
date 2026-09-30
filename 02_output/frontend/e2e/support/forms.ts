import { expect, type Locator, type Page } from '@playwright/test';

export const CAPTCHA_LABEL = 'Local test captcha: I am not a robot';

export interface ExternalValues {
  firstName: string;
  lastName: string;
  email: string;
  organization: string;
}

export interface StudentValues {
  firstName: string;
  lastName: string;
  email: string;
  studyInstitution: string;
  studyProgramme: string;
  studentId: string;
}

/** Presses Tab until the locator has focus (keyboard-only navigation). */
export async function tabTo(page: Page, target: Locator, maxTabs = 80): Promise<void> {
  for (let i = 0; i < maxTabs; i += 1) {
    if (await target.evaluate((el) => el === document.activeElement)) return;
    await page.keyboard.press('Tab');
  }
  throw new Error('element not reachable with Tab');
}

export async function consentCheckbox(page: Page): Promise<Locator> {
  const catalog = (await (await page.request.get('/api/catalog')).json()) as {
    consent: { text: string } | null;
  };
  return page.getByRole('checkbox', { name: catalog.consent?.text ?? '', exact: true });
}

export async function fillExternal(page: Page, v: ExternalValues): Promise<void> {
  await page.getByLabel('First name').fill(v.firstName);
  await page.getByLabel('Last name').fill(v.lastName);
  await page.getByLabel('Email').fill(v.email);
  await page.getByLabel('Organization / institution').fill(v.organization);
}

export async function fillStudent(page: Page, v: StudentValues): Promise<void> {
  await page.getByLabel('First name').fill(v.firstName);
  await page.getByLabel('Last name').fill(v.lastName);
  await page.getByLabel('Email').fill(v.email);
  await page.getByLabel('Study institution').fill(v.studyInstitution);
  await page.getByLabel('Study programme').fill(v.studyProgramme);
  await page.getByLabel('Student ID').fill(v.studentId);
}

export async function confirmCaptchaAndConsent(page: Page): Promise<void> {
  await (await consentCheckbox(page)).check();
  await page.getByRole('checkbox', { name: CAPTCHA_LABEL }).check();
}

export async function registrationIdShown(page: Page): Promise<string> {
  await expect(page.getByRole('heading', { name: 'Registration received' })).toBeVisible();
  const id = (await page.getByTestId('registration-id').textContent())?.trim() ?? '';
  expect(id).toMatch(/^[0-9a-f-]{36}$/);
  return id;
}

/** Asserts the field is marked invalid and its description (error text) is programmatically linked. */
export async function expectFieldError(page: Page, label: string): Promise<void> {
  const field = page.getByLabel(label, { exact: true });
  await expect(field).toHaveAttribute('aria-invalid', 'true');
  const describedBy = (await field.getAttribute('aria-describedby')) ?? '';
  expect(describedBy).not.toBe('');
  const texts = await Promise.all(
    describedBy.split(/\s+/).map((id) => page.locator(`[id="${id}"]`).textContent()),
  );
  expect(texts.join(' ')).toContain(label);
}
