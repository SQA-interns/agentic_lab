/** Every user-visible text (docs/02_contracts/ui-registration-form.json, D-18). */
export const texts = {
  pageHeading: "Conference registration",
  loading: "Loading…",
  loadError: "The registration form could not be loaded. Please try again later.",
  typeSelector: "Registration type",
  types: { EXTERNAL: "External participant", STUDENT: "Student" },
  fields: {
    firstName: "First name",
    lastName: "Last name",
    email: "Email",
    organization: "Organization / institution",
    studyInstitution: "Study institution",
    studyProgramme: "Study programme",
    studentId: "Student ID",
  },
  categories: {
    WORKSHOP: "Workshops",
    EVENT: "Events",
    MEAL: "Meals",
    OTHER: "Other activities",
  },
  consentRequired: "This consent is required.",
  robotTestMode: "I am not a robot (test mode)",
  robotRequired: "Please confirm that you are not a robot.",
  submit: "Register",
  submitBusy: "Sending…",
  fieldErrors: {
    REQUIRED: "This field is required.",
    INVALID_FORMAT: "Enter a valid email address.",
    TOO_LONG: "This value is too long.",
    CONTROL_CHARACTER: "This value contains characters that are not allowed.",
    CATEGORY_LIMIT: "Too many options selected in this group.",
  },
  networkError: "The registration could not be sent. Please check your connection and try again.",
  confirmationHeading: "Registration received",
  confirmationText: (firstName: string, email: string) =>
    `Thank you, ${firstName}. A confirmation email is on its way to ${email}.`,
} as const;
