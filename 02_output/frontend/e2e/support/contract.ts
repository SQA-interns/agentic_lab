import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// The UI contract (docs/02_contracts/registration-form.ui.json); the tests use its labels.
export type RegistrationType = 'EXTERNAL' | 'STUDENT';

interface FieldContract {
  field: string;
  label: string;
  inputType: string;
}

interface UiContract {
  pageHeading: string;
  typeChoice: { name: string; choices: { type: RegistrationType; label: string }[] };
  forms: Record<RegistrationType, FieldContract[]>;
  options: { groups: { category: string; legend: string }[] };
  submit: { name: string };
  confirmation: { heading: string };
  errors: { messages: Record<string, string> };
}

const here = dirname(fileURLToPath(import.meta.url));
const contractPath = join(
  here,
  '..',
  '..',
  '..',
  'docs',
  '02_contracts',
  'registration-form.ui.json',
);

export const ui: UiContract = JSON.parse(readFileSync(contractPath, 'utf8')) as UiContract;

export const CAPTCHA_TEST_LABEL = 'I am not a robot (test mode)';

export function labelOf(type: RegistrationType, field: string): string {
  const entry = ui.forms[type].find((f) => f.field === field);
  if (!entry) {
    throw new Error(`no field ${field} in the ${type} form contract`);
  }
  return entry.label;
}

export function typeLabel(type: RegistrationType): string {
  const choice = ui.typeChoice.choices.find((c) => c.type === type);
  if (!choice) {
    throw new Error(`no type choice ${type}`);
  }
  return choice.label;
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}@example.si`;
}
