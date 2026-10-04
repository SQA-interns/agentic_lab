import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';
import { CAPTCHA_TEST_LABEL, labelOf, typeLabel, ui, type RegistrationType } from './contract';

export interface FormConfig {
  consent: { id: string; text: string };
  options: { id: string; name: string; category: string }[];
}

export async function formConfig(api: APIRequestContext): Promise<FormConfig> {
  const response = await api.get('/api/form-config');
  expect(response.status(), 'form-config status').toBe(200);
  return (await response.json()) as FormConfig;
}

// Page object for the registration page, using only the roles and labels of the UI contract.
export class RegistrationPage {
  constructor(readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/');
    await expect(this.page.getByRole('heading', { level: 1, name: ui.pageHeading })).toBeVisible();
  }

  async chooseType(type: RegistrationType): Promise<void> {
    await this.page
      .getByRole('radiogroup', { name: ui.typeChoice.name })
      .getByRole('radio', { name: typeLabel(type), exact: true })
      .check();
  }

  field(type: RegistrationType, name: string): Locator {
    return this.page.getByLabel(labelOf(type, name), { exact: true });
  }

  async fill(type: RegistrationType, values: Record<string, string>): Promise<void> {
    for (const [name, value] of Object.entries(values)) {
      await this.field(type, name).fill(value);
    }
  }

  group(legend: string): Locator {
    return this.page.getByRole('group', { name: legend, exact: true });
  }

  option(name: string): Locator {
    return this.page.getByRole('checkbox', { name, exact: true });
  }

  consent(text: string): Locator {
    return this.page.getByRole('checkbox', { name: text, exact: true });
  }

  async passCaptcha(): Promise<void> {
    await this.page.getByRole('checkbox', { name: CAPTCHA_TEST_LABEL, exact: true }).check();
  }

  async submit(): Promise<void> {
    await this.page.getByRole('button', { name: ui.submit.name, exact: true }).click();
  }

  confirmation(): Locator {
    return this.page.getByRole('status');
  }

  formError(): Locator {
    return this.page.getByRole('alert');
  }

  async expectFieldError(type: RegistrationType, name: string, code: string): Promise<void> {
    const input = this.field(type, name);
    await expect(input).toHaveAttribute('aria-invalid', 'true');
    await expect(input).toHaveAccessibleDescription(ui.errors.messages[code]);
  }
}
