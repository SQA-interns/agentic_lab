import { fireEvent, render, screen, within } from "@testing-library/react";
import { vi } from "vitest";
import App from "../App";

/** GET /api/form-config response (openapi.yaml FormConfig), from conference-config.json. */
export const formConfig = {
  conferenceName: "Konferenca 2026",
  categories: [
    { id: "WORKSHOP", maxSelections: 2 },
    { id: "EVENT" },
    { id: "MEAL" },
    { id: "OTHER" },
  ],
  options: [
    {
      id: "ws-ai-research",
      displayName: "Workshop: AI in research",
      category: "WORKSHOP",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "ws-open-data",
      displayName: "Workshop: Open data",
      category: "WORKSHOP",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "ws-industry-lab",
      displayName: "Workshop: Industry lab",
      category: "WORKSHOP",
      availableTo: ["EXTERNAL"],
    },
    {
      id: "ev-welcome",
      displayName: "Welcome reception",
      category: "EVENT",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "ev-gala-dinner",
      displayName: "Gala dinner",
      category: "EVENT",
      availableTo: ["EXTERNAL"],
    },
    {
      id: "meal-lunch-day1",
      displayName: "Lunch, day 1",
      category: "MEAL",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "meal-lunch-day2",
      displayName: "Lunch, day 2",
      category: "MEAL",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "meal-vegetarian",
      displayName: "Vegetarian meals",
      category: "MEAL",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "other-city-tour",
      displayName: "Ljubljana city tour",
      category: "OTHER",
      availableTo: ["EXTERNAL", "STUDENT"],
    },
  ],
  consents: [
    {
      id: "data-processing",
      text: "I agree that the organizers process the personal data I enter in this form to organise the conference and my participation in it.",
    },
  ],
  antiAutomation: { mode: "test" },
};

export const CONSENT_TEXT = formConfig.consents[0]!.text;
export const TEST_MODE_CHECKBOX = "I am not a robot (test mode)";

type Reply = { status: number; body: unknown } | "network-error";

export interface FakeBackend {
  fetch: ReturnType<typeof vi.fn>;
  /** Bodies of every POST /api/registrations, parsed. */
  posted: () => Record<string, unknown>[];
}

/** Replaces fetch with the backend of openapi.yaml; POST /api/registrations answers with reply. */
export function fakeBackend(reply: Reply = { status: 201, body: accepted() }): FakeBackend {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.endsWith("/api/form-config") && method === "GET") {
      return json(200, formConfig);
    }
    if (url.endsWith("/api/registrations") && method === "POST") {
      if (reply === "network-error") {
        throw new TypeError("Failed to fetch");
      }
      return json(reply.status, reply.body);
    }
    return json(404, { code: "INTERNAL_ERROR", message: "not found" });
  });
  vi.stubGlobal("fetch", fetchMock);
  return {
    fetch: fetchMock,
    posted: () =>
      fetchMock.mock.calls
        .filter(([, init]) => (init?.method ?? "GET").toUpperCase() === "POST")
        .map(([, init]) => JSON.parse(String(init?.body)) as Record<string, unknown>),
  };
}

export function accepted(overrides: Record<string, unknown> = {}) {
  return {
    id: "3f1c2b7e-8a4d-4c1e-9b2f-6d5e4a3b2c1d",
    type: "EXTERNAL",
    firstName: "Janez",
    lastName: "Kovačič",
    email: "janez.kovacic@example.com",
    options: [
      { id: "ws-ai-research", displayName: "Workshop: AI in research", category: "WORKSHOP" },
    ],
    registeredAt: "2026-10-06T12:00:00Z",
    ...overrides,
  };
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Renders the app and waits until the form configuration is loaded. */
export async function renderApp() {
  render(<App />);
  await screen.findByRole("button", { name: "Register" });
}

export function chooseType(label: "External participant" | "Student") {
  const group = screen.getByRole("radiogroup", { name: "Registration type" });
  fireEvent.click(within(group).getByRole("radio", { name: label }));
}

export function fill(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

export function fillExternal() {
  fill("First name", "Janez");
  fill("Last name", "Kovačič");
  fill("Email", "janez.kovacic@example.com");
  fill("Organization / institution", "Inštitut Jožef Stefan");
}

export function check(name: string) {
  fireEvent.click(screen.getByRole("checkbox", { name }));
}

export function confirmConsentAndRobot() {
  check(CONSENT_TEXT);
  check(TEST_MODE_CHECKBOX);
}

export function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Register" }));
}

/** The visible label of a form control: aria-label, a label[for] or a wrapping label. */
export function labelOf(control: HTMLElement): string {
  const ariaLabel = control.getAttribute("aria-label");
  if (ariaLabel) {
    return ariaLabel.trim();
  }
  const id = control.getAttribute("id");
  const label = (id && document.querySelector(`label[for="${id}"]`)) || control.closest("label");
  return (label?.textContent ?? "").trim();
}

/** Labels of every text input, in order. */
export function textFieldLabels(): string[] {
  return screen.getAllByRole("textbox").map(labelOf);
}
