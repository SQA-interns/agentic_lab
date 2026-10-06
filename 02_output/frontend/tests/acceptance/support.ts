import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createElement } from "react";
import { expect, vi } from "vitest";
import { App } from "../../src/App";

/** Form setup as returned by GET /api/options (openapi.yaml RegistrationSetup). */
export const SETUP = {
  conferenceName: "Konferenca 2026",
  consent: {
    id: "data-processing",
    text: "I agree to the processing of my personal data for organising the conference.",
  },
  recaptcha: { testMode: true, siteKey: "" },
  options: [
    { id: "ws-ai", name: "AI workshop", category: "WORKSHOP", offeredTo: ["EXTERNAL", "STUDENT"] },
    { id: "ev-gala", name: "Gala dinner", category: "EVENT", offeredTo: ["EXTERNAL"] },
    { id: "meal-lunch", name: "Lunch", category: "MEAL", offeredTo: ["EXTERNAL", "STUDENT"] },
    {
      id: "other-poster",
      name: "Poster session",
      category: "OTHER",
      offeredTo: ["EXTERNAL", "STUDENT"],
    },
  ],
};

export const MESSAGES = {
  REQUIRED: "This field is required.",
  INVALID_EMAIL: "Enter a valid email address.",
  CONSENT_REQUIRED: "You must give this consent to register.",
  RECAPTCHA_FAILED: "Please confirm that you are not a robot.",
  DUPLICATE_EMAIL: "This email address is already registered. Please contact the organizers.",
  GENERAL: "Your registration could not be processed. Please try again later.",
  CONFIRMATION: "Thank you, your registration has been received.",
};

export type Reply = { status: number; body?: unknown } | Error;

/** Stubs fetch: GET /api/options returns SETUP, POST /api/registrations returns the reply. */
export function stubApi(reply: Reply = { status: 201, body: created() }) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/options") && method === "GET") {
      return json(200, SETUP);
    }
    if (url.endsWith("/api/registrations") && method === "POST") {
      if (reply instanceof Error) {
        throw reply;
      }
      const type = reply.status < 300 ? "application/json" : "application/problem+json";
      return json(reply.status, reply.body ?? {}, type);
    }
    return json(404, { title: "Not Found", status: 404 }, "application/problem+json");
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

export function created() {
  return {
    registrationId: "0b9f7a52-5c1e-4c55-9d1e-3f1f1f6a2b10",
    receivedAt: "2026-10-06T18:00:00Z",
  };
}

function json(status: number, body: unknown, type = "application/json") {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": type } });
}

/** Renders the app and waits until the form is ready. */
export async function renderForm() {
  render(createElement(App));
  await screen.findByTestId("registration-form");
}

export function input(testId: string): HTMLInputElement {
  return screen.getByTestId(testId) as HTMLInputElement;
}

export function type(testId: string, value: string) {
  fireEvent.change(input(testId), { target: { value } });
  fireEvent.blur(input(testId));
}

export function check(testId: string) {
  fireEvent.click(input(testId));
}

export function fillExternal(overrides: Record<string, string> = {}) {
  const values = {
    firstName: "Ana",
    lastName: "Novak",
    email: "ana.novak@example.si",
    organization: "Institut Jožef Stefan",
    ...overrides,
  };
  for (const [k, v] of Object.entries(values)) {
    type(k, v);
  }
}

export function fillStudent(overrides: Record<string, string> = {}) {
  check("type-STUDENT");
  const values = {
    firstName: "Luka",
    lastName: "Kranjc",
    email: "luka@example.si",
    studyInstitution: "Univerza v Mariboru",
    studyProgramme: "Informatika",
    studentId: "93120001",
    ...overrides,
  };
  for (const [k, v] of Object.entries(values)) {
    type(k, v);
  }
}

export function submit() {
  fireEvent.click(screen.getByTestId("submit"));
}

/** Body of the POST /api/registrations call, or undefined if none was made. */
export function postedBody(fetchMock: ReturnType<typeof stubApi>) {
  const call = fetchMock.mock.calls.find(([, init]) => (init?.method ?? "GET") === "POST");
  return call ? JSON.parse(String(call[1]?.body)) : undefined;
}

export async function waitForPost(fetchMock: ReturnType<typeof stubApi>) {
  await waitFor(() => expect(postedBody(fetchMock)).toBeDefined());
  return postedBody(fetchMock);
}
