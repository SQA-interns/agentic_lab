import type { Category, RegistrationType, TextFieldName } from "./types";

// Every text of the page in one place (D-18: English, translatable).

export const messages = {
  title: "Conference registration",
  loading: "Loading the registration form…",
  loadFailed: "The registration form could not be loaded. Please try again later.",
  typeLegend: "Registration type",
  types: { EXTERNAL: "External participant", STUDENT: "Student" } satisfies Record<
    RegistrationType,
    string
  >,
  fields: {
    firstName: "First name",
    lastName: "Last name",
    email: "Email",
    organization: "Organization / institution",
    studyInstitution: "Study institution",
    studyProgramme: "Study programme",
    studentId: "Student ID",
  } satisfies Record<TextFieldName, string>,
  categories: {
    workshop: "Workshops",
    event: "Events",
    meal: "Meals",
    other: "Other activities",
  } satisfies Record<Category, string>,
  consentsLegend: "Consent",
  captchaTest: "I am not a robot (test mode)",
  submit: "Register",
  submitting: "Sending…",
  required: (label: string) => `${label} is required.`,
  invalidCharacters: (label: string) =>
    `${label} must not contain line breaks or other control characters.`,
  tooLong: (label: string, max: number) => `${label} must be at most ${max} characters.`,
  invalidEmail: "Enter a valid email address.",
  tooManyOptions: (max: number, category: Category) =>
    `Select at most ${max} of ${messages.categories[category].toLowerCase()}.`,
  consentRequired: "Please give the required consent.",
  captchaRequired: "Please confirm that you are not a robot.",
  confirmation: (firstName: string, lastName: string) =>
    `Thank you, ${firstName} ${lastName}. Your registration has been received.`,
  registrationId: "Registration ID",
  serverErrors: {
    captcha_failed: "Please confirm that you are not a robot.",
    captcha_unavailable: "The anti-automation check is unavailable. Please try again later.",
    rate_limited: "Too many attempts. Please wait a minute and try again.",
    payload_too_large: "The registration is too large.",
  } as Record<string, string>,
  genericError: "The registration could not be processed. Please try again later.",
};
