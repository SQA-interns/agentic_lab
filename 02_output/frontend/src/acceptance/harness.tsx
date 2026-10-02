// Harness of the frontend acceptance tests: renders the application as a user sees it and
// replaces the network with an in-memory backend that follows docs/02_contracts/openapi.yaml.
// The tests use only the rendered page and the HTTP calls, never the application's modules.
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, vi } from "vitest";
import { App } from "../App";

export interface Option {
  id: string;
  name: string;
  category: "workshop" | "event" | "meal" | "other";
}

export interface Call {
  method: string;
  path: string;
  body: Record<string, unknown> | undefined;
}

export interface Answer {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export const CONSENT_TEXT = "Soglašam z obdelavo osebnih podatkov za namen prijave na konferenco.";

export const ACTIVE_OPTIONS: Option[] = [
  { id: "ws-testing", name: "Delavnica: testiranje programske opreme", category: "workshop" },
  { id: "ws-security", name: "Delavnica: varnost spletnih aplikacij", category: "workshop" },
  { id: "ev-opening", name: "Otvoritvena slovesnost", category: "event" },
  { id: "ev-dinner", name: "Slavnostna večerja", category: "event" },
  { id: "meal-lunch-day1", name: "Kosilo, prvi dan", category: "meal" },
  { id: "meal-vegetarian", name: "Vegetarijanski meni", category: "meal" },
  { id: "other-city-tour", name: "Voden ogled mesta", category: "other" },
];

export const MESSAGES = {
  required: "To polje je obvezno.",
  invalidEmail: "Vnesite veljaven e-poštni naslov.",
  optionNotSelectable: "Izbrana možnost ni na voljo.",
  consentRequired: "Za prijavo je potrebno soglasje.",
  captchaFailed: "Potrdite, da niste robot.",
  notReceived: "Prijave nismo prejeli. Poskusite znova pozneje.",
  tooManyRequests: "Preveč poskusov. Počakajte trenutek in poskusite znova.",
  confirmationHeading: "Prijava je sprejeta",
};

export const LABELS = {
  typeLegend: /^Vrsta prijave/,
  external: /^Zunanji udeleženec/,
  student: /^Študent/,
  firstName: /^Ime(?![a-zčšž])/i,
  lastName: /^Priimek/,
  email: /^E-pošta/,
  organization: /^Organizacija \/ ustanova/,
  studyInstitution: /^Izobraževalna ustanova/,
  studyProgramme: /^Študijski program/,
  studentId: /^Vpisna številka/,
  captcha: /^Nisem robot/,
  submit: /^Oddaj prijavo/,
};

export function validationProblem(errors: FieldError[]): Answer {
  return {
    status: 400,
    headers: { "Content-Type": "application/problem+json" },
    body: { type: "about:blank", title: "Neveljavna prijava", status: 400, errors },
  };
}

/** What the contract answers to a registration that reaches the backend. */
function defaultRegistrationAnswer(body: Record<string, unknown> | undefined): Answer {
  if (body?.consent !== true) {
    return validationProblem([
      { field: "consent", code: "consent_required", message: MESSAGES.consentRequired },
    ]);
  }
  if (body.captchaToken !== "test-pass") {
    return validationProblem([
      { field: "captchaToken", code: "captcha_failed", message: MESSAGES.captchaFailed },
    ]);
  }
  return {
    status: 201,
    body: { id: "3f0e7a52-8a54-4d0e-9f43-1c6a2b7d9e10", acceptedAt: "2026-10-02T10:15:30.123Z" },
  };
}

export interface Backend {
  /** Every HTTP call the application made, in order. */
  calls: Call[];
  /** The registration submissions among the calls. */
  registrations: () => Call[];
}

export interface BackendSetup {
  options?: Option[];
  /** Replaces the default answer to POST /api/registrations. */
  onRegistration?: (body: Record<string, unknown> | undefined) => Answer | Promise<Answer>;
}

async function toCall(input: RequestInfo | URL, init?: RequestInit): Promise<Call> {
  const request = typeof input === "object" && "url" in input ? input : undefined;
  const url = new URL(request ? request.url : String(input), "http://localhost");
  const method = (init?.method ?? request?.method ?? "GET").toUpperCase();
  let text: string | undefined;
  if (typeof init?.body === "string") {
    text = init.body;
  } else if (request && method !== "GET") {
    text = await request.clone().text();
  }
  return {
    method,
    path: url.pathname,
    body: text ? (JSON.parse(text) as Record<string, unknown>) : undefined,
  };
}

function toResponse(answer: Answer): Response {
  return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
    status: answer.status,
    headers: { "Content-Type": "application/json", ...answer.headers },
  });
}

/** Installs the in-memory backend in place of the network. */
export function installBackend(setup: BackendSetup = {}): Backend {
  const calls: Call[] = [];
  const fetchStub = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const call = await toCall(input, init);
    calls.push(call);
    if (call.method === "GET" && call.path === "/api/form-config") {
      return toResponse({
        status: 200,
        body: {
          conferenceName: "Testna konferenca 2027",
          consent: { id: "personal-data", text: CONSENT_TEXT },
          captcha: { mode: "test", siteKey: "" },
        },
      });
    }
    if (call.method === "GET" && call.path === "/api/options") {
      return toResponse({ status: 200, body: { options: setup.options ?? ACTIVE_OPTIONS } });
    }
    if (call.method === "POST" && call.path === "/api/registrations") {
      const answer = await (setup.onRegistration ?? defaultRegistrationAnswer)(call.body);
      return toResponse(answer);
    }
    return toResponse({
      status: 404,
      body: { type: "about:blank", title: "Not found", status: 404 },
    });
  };
  vi.stubGlobal("fetch", vi.fn(fetchStub));
  return {
    calls,
    registrations: () =>
      calls.filter((call) => call.method === "POST" && call.path === "/api/registrations"),
  };
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** Renders the application and waits until the registration type can be chosen. */
export async function openRegistrationPage(): Promise<void> {
  render(<App />);
  await screen.findByRole("radiogroup", { name: LABELS.typeLegend });
}

export function chooseType(type: "EXTERNAL" | "STUDENT"): void {
  const label = type === "EXTERNAL" ? LABELS.external : LABELS.student;
  fireEvent.click(screen.getByRole("radio", { name: label }));
}

export function textField(label: RegExp): HTMLInputElement {
  return screen.getByRole<HTMLInputElement>("textbox", { name: label });
}

export function enter(label: RegExp, value: string): void {
  fireEvent.change(textField(label), { target: { value } });
}

export function checkbox(name: string | RegExp): HTMLInputElement {
  return screen.getByRole<HTMLInputElement>("checkbox", { name });
}

export function tick(name: string | RegExp): void {
  fireEvent.click(checkbox(name));
}

export function submit(): void {
  fireEvent.click(screen.getByRole("button", { name: LABELS.submit }));
}

export function fillExternalFields(): void {
  enter(LABELS.firstName, "Živa");
  enter(LABELS.lastName, "Čučnik Šušteršič");
  enter(LABELS.email, "ziva.cucnik@example.org");
  enter(LABELS.organization, "Inštitut za računalništvo");
}

export function fillStudentFields(): void {
  enter(LABELS.firstName, "Žan");
  enter(LABELS.lastName, "Košir");
  enter(LABELS.email, "zan.kosir@example.org");
  enter(LABELS.studyInstitution, "Univerza v Ljubljani");
  enter(LABELS.studyProgramme, "Računalništvo in informatika");
  enter(LABELS.studentId, "63210001");
}

export function giveConsentAndPassCaptcha(): void {
  tick(CONSENT_TEXT);
  tick(LABELS.captcha);
}

export const GROUP_LEGENDS = ["Delavnice", "Dogodki", "Obroki", "Druge aktivnosti"];

/**
 * The option checkboxes inside the group with the given legend, in page order, each identified
 * by its accessible name among `knownNames`; a checkbox with another name is "(unknown)".
 */
export function optionsInGroup(
  legend: string,
  knownNames: string[] = ACTIVE_OPTIONS.map((option) => option.name),
): string[] {
  const group = within(screen.getByRole("group", { name: legend }));
  return group
    .getAllByRole("checkbox")
    .map(
      (box) =>
        knownNames.find((name) => group.queryByRole("checkbox", { name }) === box) ?? "(unknown)",
    );
}

/** The names of all option checkboxes on the form, group by group in page order. */
export function offeredOptionNames(knownNames?: string[]): string[] {
  return GROUP_LEGENDS.flatMap((legend) =>
    screen.queryByRole("group", { name: legend }) ? optionsInGroup(legend, knownNames) : [],
  );
}

export function confirmationShown(): boolean {
  return screen.queryByText(MESSAGES.confirmationHeading) !== null;
}
