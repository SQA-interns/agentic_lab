// Labels and messages of the registration form (docs/02_contracts/registration-form.ui.json).
import type { FieldName, OptionCategory, RegistrationType } from "./api";

export const PAGE_TITLE = "Prijava na konferenco";
export const TYPE_LEGEND = "Vrsta prijave";
export const TYPE_LABELS: Record<RegistrationType, string> = {
  EXTERNAL: "Zunanji udeleženec",
  STUDENT: "Študent",
};

export interface FieldSpec {
  name: FieldName;
  label: string;
  control: "text" | "email";
  maxLength: number;
  autoComplete?: string;
}

const FIRST_NAME: FieldSpec = {
  name: "firstName",
  label: "Ime",
  control: "text",
  maxLength: 100,
  autoComplete: "given-name",
};
const LAST_NAME: FieldSpec = {
  name: "lastName",
  label: "Priimek",
  control: "text",
  maxLength: 100,
  autoComplete: "family-name",
};
const EMAIL: FieldSpec = {
  name: "email",
  label: "E-pošta",
  control: "email",
  maxLength: 254,
  autoComplete: "email",
};

export const FORM_FIELDS: Record<RegistrationType, FieldSpec[]> = {
  EXTERNAL: [
    FIRST_NAME,
    LAST_NAME,
    EMAIL,
    {
      name: "organization",
      label: "Organizacija / ustanova",
      control: "text",
      maxLength: 200,
      autoComplete: "organization",
    },
  ],
  STUDENT: [
    FIRST_NAME,
    LAST_NAME,
    EMAIL,
    { name: "studyInstitution", label: "Izobraževalna ustanova", control: "text", maxLength: 200 },
    { name: "studyProgramme", label: "Študijski program", control: "text", maxLength: 200 },
    { name: "studentId", label: "Vpisna številka", control: "text", maxLength: 50 },
  ],
};

export const OPTION_GROUPS: { category: OptionCategory; legend: string }[] = [
  { category: "workshop", legend: "Delavnice" },
  { category: "event", legend: "Dogodki" },
  { category: "meal", legend: "Obroki" },
  { category: "other", legend: "Druge aktivnosti" },
];

export const CAPTCHA_TEST_LABEL = "Nisem robot (testni način)";
export const SUBMIT_LABEL = "Oddaj prijavo";

export const CONFIRMATION = {
  heading: "Prijava je sprejeta",
  text: "Vašo prijavo smo prejeli. Potrditev smo poslali na vaš e-poštni naslov.",
};

export const FAILURES = {
  notReceived: "Prijave nismo prejeli. Poskusite znova pozneje.",
  tooManyRequests: "Preveč poskusov. Počakajte trenutek in poskusite znova.",
  loadFailed: "Obrazca ni bilo mogoče naložiti. Osvežite stran.",
};

/** Text shown next to a field, by error code of the API contract. */
export const MESSAGES: Record<string, string> = {
  required: "To polje je obvezno.",
  too_long: "Vnos je predolg.",
  invalid_format: "Vnesite veljaven e-poštni naslov.",
  invalid_characters: "Vnos vsebuje nedovoljene znake.",
  option_not_selectable: "Izbrana možnost ni na voljo.",
  option_duplicate: "Možnost je izbrana večkrat.",
  consent_required: "Za prijavo je potrebno soglasje.",
  captcha_failed: "Potrdite, da niste robot.",
};

export const FALLBACK_MESSAGE = "Vrednost ni veljavna.";
