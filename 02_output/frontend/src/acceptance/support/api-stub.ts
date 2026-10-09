import { vi } from "vitest";

/** Form data as GET /api/registration-form returns it (openapi.yaml, RegistrationForm). */
export const formData = {
  conferenceName: "Konferenca 2026",
  captcha: { mode: "test", siteKey: null },
  categories: [
    {
      category: "workshop",
      maxSelections: 1,
      options: [
        { id: "ws-ai", name: "Delavnica umetne inteligence", availableTo: ["EXTERNAL", "STUDENT"] },
        {
          id: "ws-security",
          name: "Varnost spletnih aplikacij",
          availableTo: ["EXTERNAL", "STUDENT"],
        },
      ],
    },
    {
      category: "event",
      maxSelections: 2,
      options: [
        { id: "ev-reception", name: "Welcome reception", availableTo: ["EXTERNAL", "STUDENT"] },
        { id: "ev-industry-dinner", name: "Industry dinner", availableTo: ["EXTERNAL"] },
      ],
    },
    {
      category: "meal",
      maxSelections: 2,
      options: [
        { id: "meal-lunch-1", name: "Lunch, day 1", availableTo: ["EXTERNAL", "STUDENT"] },
        { id: "meal-lunch-2", name: "Lunch, day 2", availableTo: ["EXTERNAL", "STUDENT"] },
      ],
    },
    {
      category: "other",
      maxSelections: 1,
      options: [{ id: "other-career-fair", name: "Career fair", availableTo: ["STUDENT"] }],
    },
  ],
  consents: [
    {
      id: "data-processing",
      text: "I agree to the processing of my personal data for the registration and organisation of the conference.",
      mandatory: true,
    },
  ],
};

export interface StubResponse {
  status: number;
  body: unknown;
}

export interface RecordedCall {
  url: string;
  method: string;
  body: unknown;
}

/**
 * Replaces fetch: GET /api/registration-form answers {@link formData}; POST /api/registrations
 * answers {@code submit}. Returns the calls made, in order.
 */
export function stubApi(submit: StubResponse = created()): RecordedCall[] {
  const calls: RecordedCall[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      const method = (init?.method ?? "GET").toUpperCase();
      const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
      calls.push({ url, method, body });
      const reply =
        method === "GET" && url.endsWith("/api/registration-form")
          ? { status: 200, body: formData }
          : method === "POST" && url.endsWith("/api/registrations")
            ? submit
            : { status: 404, body: { error: "not_found", message: "Not found", fieldErrors: [] } };
      return new Response(JSON.stringify(reply.body), {
        status: reply.status,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
  return calls;
}

export function created(): StubResponse {
  return {
    status: 201,
    body: {
      registrationId: "3f1c2a9e-8d4b-4c6a-9b1e-2f7d5e8a1c40",
      registeredAt: "2026-10-09T08:15:30.123Z",
      type: "EXTERNAL",
    },
  };
}

export function registrationPosts(calls: RecordedCall[]): RecordedCall[] {
  return calls.filter((call) => call.method === "POST" && call.url.endsWith("/api/registrations"));
}
