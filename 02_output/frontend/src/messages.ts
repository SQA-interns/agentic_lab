// Texts of docs/02_contracts/ui-form.json.

export const messages: Record<string, string> = {
  REQUIRED: "This field is required.",
  INVALID_EMAIL: "Enter a valid email address.",
  TOO_LONG: "This value is too long.",
  OPTION_NOT_AVAILABLE: "One of the selected options is not available.",
  TOO_MANY_OPTIONS: "Too many options selected in this category.",
  CONSENT_REQUIRED: "This consent is required.",
  UNKNOWN_CONSENT: "The form is out of date. Reload the page.",
  ALREADY_REGISTERED: "A registration with this email already exists.",
  CAPTCHA_FAILED: "Please confirm that you are not a robot.",
  MALFORMED: "The form could not be submitted. Reload the page.",
  GENERAL: "Your registration could not be saved. Please try again later.",
  RATE_LIMITED: "Too many attempts. Please wait a minute and try again.",
};

export const fieldLabels: Record<string, string> = {
  firstName: "First name",
  lastName: "Last name",
  email: "Email",
  organization: "Organization / institution",
  studyInstitution: "Study institution",
  studyProgramme: "Study programme",
  studentId: "Student ID",
};

export const typeLabels = {
  EXTERNAL: "External participant",
  STUDENT: "Student",
} as const;

export const categoryHeadings: Record<string, string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};

export function message(code: string): string {
  return messages[code] ?? messages.GENERAL;
}
