// Contract fixtures (api.openapi.yaml FormConfig) and a fetch mock for UI acceptance tests.
import { vi } from "vitest";

export const formConfig = {
  conferenceName: "Acceptance Konferenca",
  recaptcha: { siteKey: "", testMode: true },
  categories: [
    { category: "WORKSHOP", maxSelections: 1 },
    { category: "EVENT", maxSelections: 3 },
    { category: "MEAL", maxSelections: 3 },
    { category: "OTHER", maxSelections: 2 },
  ],
  options: [
    {
      id: "ws-testing",
      name: "Delavnica: testiranje programske opreme",
      category: "WORKSHOP",
      registrationTypes: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "ws-security",
      name: "Workshop: application security",
      category: "WORKSHOP",
      registrationTypes: ["EXTERNAL"],
    },
    {
      id: "ev-reception",
      name: "Welcome reception",
      category: "EVENT",
      registrationTypes: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "ev-career-fair",
      name: "Career fair",
      category: "EVENT",
      registrationTypes: ["STUDENT"],
    },
    {
      id: "meal-dinner",
      name: "Conference dinner (vegetarian option)",
      category: "MEAL",
      registrationTypes: ["EXTERNAL", "STUDENT"],
    },
    {
      id: "other-city-tour",
      name: "Ljubljana city tour",
      category: "OTHER",
      registrationTypes: ["EXTERNAL", "STUDENT"],
    },
  ],
  consents: [
    {
      id: "data-processing",
      text: "I agree that my personal data is processed for registering me for the conference.",
      mandatory: true,
    },
  ],
};

export interface Call {
  url: string;
  method: string;
  body: unknown;
}

export interface MockedApi {
  calls: Call[];
  registrationCalls: () => Call[];
}

type Reply = { status: number; body: unknown; contentType?: string };

/**
 * Replaces fetch: GET /api/form answers with the fixture, POST /api/registrations with `reply`.
 * `reply` may be a promise to keep a submission pending.
 */
export function mockApi(reply: Reply | Promise<Reply> = accepted()): MockedApi {
  const calls: Call[] = [];
  const respond = (r: Reply) =>
    new Response(JSON.stringify(r.body), {
      status: r.status,
      headers: { "Content-Type": r.contentType ?? "application/json" },
    });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input.toString();
      const method = (init?.method ?? "GET").toUpperCase();
      const body = typeof init?.body === "string" ? JSON.parse(init.body) : init?.body;
      calls.push({ url, method, body });
      if (url.endsWith("/api/form") && method === "GET") {
        return respond({ status: 200, body: formConfig });
      }
      if (url.endsWith("/api/registrations") && method === "POST") {
        return respond(await reply);
      }
      return respond({ status: 404, body: { title: "Not found", status: 404 } });
    }),
  );
  return {
    calls,
    registrationCalls: () => calls.filter((c) => c.url.endsWith("/api/registrations")),
  };
}

export function accepted(): Reply {
  return {
    status: 201,
    body: { id: "3f2b8c1e-7a4d-4e8b-9c61-2d5f0a9e4b17", receivedAt: "2026-10-08T21:15:00Z" },
  };
}

export function problem(status: number, title: string, errors?: unknown[]): Reply {
  return {
    status,
    contentType: "application/problem+json",
    body: { type: "about:blank", title, status, ...(errors ? { errors } : {}) },
  };
}
