const form = document.querySelector("#registration-form");
const participantFields = document.querySelector("#participant-fields");
const optionGroups = document.querySelector("#option-groups");
const consentFields = document.querySelector("#consent-fields");
const loading = document.querySelector("#loading");
const fatalError = document.querySelector("#fatal-error");
const errorSummary = document.querySelector("#error-summary");
const confirmation = document.querySelector("#confirmation");
const status = document.querySelector("#status");
const submitButton = document.querySelector("#submit");
const variant = window.location.pathname === "/student" ? "student" : "external";

let context;
let submissionId = crypto.randomUUID();

document.querySelector("#reload").addEventListener("click", loadForm);
form.addEventListener("submit", submitRegistration);
loadForm();

async function loadForm() {
  loading.hidden = false;
  fatalError.hidden = true;
  try {
    const response = await fetch(`/api/v1/forms/${variant}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) throw new Error("form unavailable");
    context = await response.json();
    renderForm(context);
    loading.hidden = true;
    form.hidden = false;
  } catch {
    loading.hidden = true;
    fatalError.hidden = false;
  }
}

function renderForm(data) {
  document.title = `${data.conferenceName} — Registration`;
  document.querySelector(".eyebrow").textContent = data.conferenceName;
  document.querySelector("#page-title").textContent =
    variant === "student" ? "Student registration" : "External participant registration";
  participantFields.replaceChildren(...data.fields.map(createTextField));
  optionGroups.replaceChildren(...createOptionGroups(data.options));
  consentFields.replaceChildren(...data.consents.map(createConsent));
}

function createTextField(definition) {
  const wrapper = document.createElement("div");
  wrapper.className = "field";
  const label = document.createElement("label");
  label.htmlFor = definition.name;
  label.textContent = definition.label;
  const input = document.createElement("input");
  input.id = definition.name;
  input.name = definition.name;
  input.type = definition.inputType;
  input.maxLength = definition.maxLength;
  input.autocomplete = definition.autocomplete;
  input.required = true;
  input.setAttribute("aria-describedby", `error-${definition.name}`);
  const error = document.createElement("p");
  error.id = `error-${definition.name}`;
  error.className = "field-error";
  error.hidden = true;
  wrapper.append(label, input, error);
  return wrapper;
}

function createOptionGroups(options) {
  const categories = new Map([
    ["workshop", "Workshops"],
    ["event", "Events"],
    ["meal", "Meals"],
    ["other", "Other activities"],
  ]);
  const groups = [];
  for (const [category, title] of categories) {
    const matching = options.filter((option) => option.category === category);
    if (matching.length === 0) continue;
    const fieldset = document.createElement("fieldset");
    const legend = document.createElement("legend");
    legend.textContent = title;
    fieldset.append(legend, ...matching.map(createOption));
    groups.push(fieldset);
  }
  return groups;
}

function createOption(option) {
  const wrapper = document.createElement("div");
  wrapper.className = "choice";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.name = "optionIds";
  input.id = `option-${option.id}`;
  input.value = option.id;
  const label = document.createElement("label");
  label.htmlFor = input.id;
  label.textContent = option.displayName;
  wrapper.append(input, label);
  return wrapper;
}

function createConsent(consent) {
  const wrapper = document.createElement("div");
  wrapper.className = "choice";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.name = "consent";
  input.id = `consent-${consent.id}`;
  input.value = consent.id;
  input.required = consent.required;
  input.setAttribute("aria-describedby", `error-consent-${consent.id}`);
  const label = document.createElement("label");
  label.htmlFor = input.id;
  label.textContent = consent.label;
  const error = document.createElement("p");
  error.id = `error-consent-${consent.id}`;
  error.className = "field-error";
  error.hidden = true;
  wrapper.append(input, label, error);
  return wrapper;
}

async function submitRegistration(event) {
  event.preventDefault();
  clearErrors();
  if (!form.checkValidity()) {
    showBrowserErrors();
    return;
  }
  const data = new FormData(form);
  const participant = {};
  for (const field of context.fields) participant[field.name] = data.get(field.name);
  const consents = {};
  for (const consent of context.consents) consents[consent.id] = data.getAll("consent").includes(consent.id);
  const payload = {
    submissionId,
    variant,
    participant,
    optionIds: data.getAll("optionIds"),
    consents,
    challengeToken: context.challengeToken,
    website: data.get("website") || "",
  };

  submitButton.disabled = true;
  status.textContent = "Submitting your registration…";
  try {
    const response = await fetch("/api/v1/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(payload),
    });
    const result = await response.json();
    if (response.ok && result.status === "registered") {
      form.hidden = true;
      confirmation.hidden = false;
      document.querySelector("#registration-id").textContent = result.registrationId;
      status.textContent = "Registration successfully processed.";
      confirmation.focus?.();
      submissionId = crypto.randomUUID();
      return;
    }
    if (result.error?.fields) showServerErrors(result.error.fields);
    showSummary(result.error?.message || "Registration was not confirmed. Please try again.");
    status.textContent = "Registration was not confirmed.";
    if (response.status === 403) await refreshChallenge();
  } catch {
    showSummary("Registration was not confirmed because the service could not be reached. You can retry safely.");
    status.textContent = "Registration was not confirmed.";
  } finally {
    submitButton.disabled = false;
  }
}

function showBrowserErrors() {
  const invalid = form.querySelectorAll(":invalid");
  for (const input of invalid) setFieldError(input, input.validationMessage);
  showSummary("Please correct the highlighted fields.");
  invalid[0]?.focus();
}

function showServerErrors(fields) {
  for (const [path, message] of Object.entries(fields)) {
    if (path.startsWith("consents.")) {
      const consent = document.querySelector(`#consent-${CSS.escape(path.split(".")[1])}`);
      if (consent) setFieldError(consent, message);
      continue;
    }
    const name = path.replace(/^participant\./, "").replaceAll("_", "");
    const input = [...form.elements].find(
      (element) => element.name?.toLowerCase() === name.toLowerCase(),
    );
    if (input) setFieldError(input, message);
  }
}

function setFieldError(input, message) {
  input.setAttribute("aria-invalid", "true");
  const error = document.querySelector(`#error-${CSS.escape(input.id)}`);
  if (error) {
    error.textContent = message;
    error.hidden = false;
  }
}

function showSummary(message) {
  errorSummary.textContent = message;
  errorSummary.hidden = false;
  errorSummary.focus();
}

function clearErrors() {
  errorSummary.hidden = true;
  for (const element of form.querySelectorAll('[aria-invalid="true"]')) element.removeAttribute("aria-invalid");
  for (const element of form.querySelectorAll(".field-error")) {
    element.hidden = true;
    element.textContent = "";
  }
}

async function refreshChallenge() {
  try {
    const response = await fetch(`/api/v1/forms/${variant}`, { cache: "no-store" });
    if (response.ok) context.challengeToken = (await response.json()).challengeToken;
  } catch {
    // The visible retry error remains sufficient if refreshing is unavailable.
  }
}
