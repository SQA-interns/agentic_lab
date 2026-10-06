// Visible texts (docs/02_contracts/registration-form.schema.json, x-messages).

export const MESSAGES: Record<string, string> = {
  REQUIRED: "This field is required.",
  INVALID_EMAIL: "Enter a valid email address.",
  TOO_LONG: "This value is too long.",
  INVALID_CHARACTERS: "This value contains characters that are not allowed.",
  NOT_ALLOWED_FOR_TYPE: "This field does not belong to the selected registration type.",
  CONSENT_REQUIRED: "You must give this consent to register.",
  RECAPTCHA_FAILED: "Please confirm that you are not a robot.",
  OPTION: "This option is not available. Reload the page and choose again.",
  DUPLICATE_EMAIL: "This email address is already registered. Please contact the organizers.",
  GENERAL: "Your registration could not be processed. Please try again later.",
  CONFIRMATION: "Thank you, your registration has been received.",
  LOAD_FAILED: "The registration form could not be loaded.",
};

export const LABELS = {
  type: "I am registering as",
  EXTERNAL: "External participant",
  STUDENT: "Student",
  firstName: "First name",
  lastName: "Last name",
  email: "Email",
  organization: "Organization / institution",
  studyInstitution: "Study institution",
  studyProgramme: "Study programme",
  studentId: "Student ID",
  submit: "Register",
  testCaptcha: "Test mode: I am not a robot",
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
} as const;

export function message(code: string): string {
  return MESSAGES[code] ?? MESSAGES.GENERAL;
}
