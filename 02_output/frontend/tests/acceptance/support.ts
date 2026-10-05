// Shared helpers of the frontend acceptance tests: a mocked REST API following
// docs/02_contracts/api.openapi.yaml and lookups by the accessible names of
// docs/02_contracts/ui-registration-form.json.
import { fireEvent, screen, within } from "@testing-library/react";
import { expect, vi } from "vitest";

export const CONSENT_TEXT =
  "I agree to the processing of the personal data entered in this form for the registration and organisation of the conference.";
export const PHOTO_CONSENT_TEXT =
  "I agree that photos taken at the conference in which I appear may be published.";

export const formConfig = {
  conferenceName: "Acceptance Conference 2026",
  options: [
    {
      id: "ws-testing",
      name: "Delavnica: testiranje",
      category: "workshop",
      offeredTo: ["external", "student"],
    },
    {
      id: "ws-industry",
      name: "Industry workshop",
      category: "workshop",
      offeredTo: ["external"],
    },
    {
      id: "ev-dinner",
      name: "Conference dinner",
      category: "event",
      offeredTo: ["external", "student"],
    },
    {
      id: "meal-lunch",
      name: "Kosilo",
      category: "meal",
      offeredTo: ["external", "student"],
    },
    {
      id: "other-tour",
      name: "City tour",
      category: "other",
      offeredTo: ["external", "student"],
    },
  ],
  consents: [
    { id: "data-processing", text: CONSENT_TEXT, mandatory: true },
    { id: "photo", text: PHOTO_CONSENT_TEXT, mandatory: false },
  ],
  captcha: { mode: "test" },
};

export interface MockResponse {
  status: number;
  body: unknown;
  contentType?: string;
}

export interface RecordedRequest {
  method: string;
  url: string;
  body: unknown;
}

/** Installs a fetch mock: form configuration on GET, the given answer on POST. */
export function mockApi(registrationAnswer: MockResponse) {
  const requests: RecordedRequest[] = [];
  const fetchMock = vi.fn(
    async (input: RequestInfo | URL, init?: RequestInit) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.href
            : input.url;
      const method = (init?.method ?? "GET").toUpperCase();
      const body =
        typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
      requests.push({ method, url, body });
      const answer: MockResponse =
        method === "GET" && url.includes("/api/form-config")
          ? { status: 200, body: formConfig }
          : method === "POST" && url.includes("/api/registrations")
            ? registrationAnswer
            : { status: 404, body: { title: "Not Found", status: 404 } };
      const contentType =
        answer.contentType ??
        (answer.status >= 400
          ? "application/problem+json"
          : "application/json");
      return new Response(JSON.stringify(answer.body), {
        status: answer.status,
        headers: { "Content-Type": contentType },
      });
    },
  );
  vi.stubGlobal("fetch", fetchMock);
  return {
    requests,
    posts: () => requests.filter((r) => r.method === "POST"),
  };
}

export const accepted = (
  overrides: Record<string, unknown> = {},
): MockResponse => ({
  status: 201,
  body: {
    registrationId: "3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c",
    type: "external",
    firstName: "Ana",
    lastName: "Novak",
    email: "ana.novak@example.com",
    options: [
      { id: "ws-testing", name: "Delavnica: testiranje", category: "workshop" },
    ],
    receivedAt: "2026-10-05T20:45:00.123Z",
    ...overrides,
  },
});

export const problem = (
  status: number,
  errors: { field: string; code: string; message: string }[] = [],
  title = "Registration rejected",
  detail?: string,
): MockResponse => ({
  status,
  body: { title, status, detail, errors },
});

/** Waits until the form has loaded its configuration. */
export async function formLoaded() {
  await screen.findByRole("button", { name: "Register" });
}

export function field(label: string): HTMLElement {
  return screen.getByLabelText(label, { exact: true });
}

export function type(label: string, value: string) {
  fireEvent.change(field(label), { target: { value } });
}

export function chooseType(label: "External participant" | "Student") {
  const group = screen.getByRole("radiogroup", { name: "Registration type" });
  fireEvent.click(within(group).getByRole("radio", { name: label }));
}

export function check(name: string | RegExp) {
  fireEvent.click(screen.getByRole("checkbox", { name }));
}

export function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Register" }));
}

/** The error element linked to a control through aria-describedby. */
export function errorOf(control: HTMLElement): HTMLElement | null {
  const ids = (control.getAttribute("aria-describedby") ?? "")
    .split(/\s+/)
    .filter(Boolean);
  for (const id of ids) {
    const element = document.getElementById(id);
    if (
      element &&
      element.getAttribute("role") === "alert" &&
      element.textContent?.trim()
    ) {
      return element;
    }
  }
  return null;
}

export function expectFieldError(control: HTMLElement, text: string) {
  const error = errorOf(control);
  expect(error, "error linked to the field").not.toBeNull();
  expect(error).toHaveTextContent(text);
  expect(control).toHaveAttribute("aria-invalid", "true");
}

export function fillExternal() {
  type("First name", "Ana");
  type("Last name", "Novak");
  type("Email", "ana.novak@example.com");
  type("Organization / institution", "Institut Jozef Stefan");
}

export function fillStudent() {
  type("First name", "Luka");
  type("Last name", "Kranjc");
  type("Email", "luka.kranjc@student.example.com");
  type("Study institution", "Univerza v Mariboru");
  type("Study programme", "Informatika");
  type("Student ID", "93120045");
}

export function confirmConsentAndCaptcha() {
  check(CONSENT_TEXT);
  check("I am not a robot (test mode)");
}
