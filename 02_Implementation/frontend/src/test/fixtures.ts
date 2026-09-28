import { vi } from "vitest";
import type { FormConfig } from "../api";

export const formConfig: FormConfig = {
  options: [
    { id: "ws-ai", name: "Workshop: AI", category: "WORKSHOP" },
    { id: "ev-dinner", name: "Conference dinner", category: "EVENT" },
    { id: "meal-lunch", name: "Lunch – day 1", category: "MEAL" },
  ],
  consents: [
    {
      id: "privacy",
      text: "I agree that my personal data is processed.",
      mandatory: true,
    },
  ],
  captcha: { mode: "TEST" },
};

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export interface RecordedCall {
  url: string;
  init?: RequestInit;
}

/** Installs a fetch mock that answers form-config and delegates submissions to `submit`. */
export function mockFetch(
  submit: (url: string, body: unknown) => Response | Promise<Response>,
  config: FormConfig = formConfig,
): RecordedCall[] {
  const calls: RecordedCall[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === "/api/form-config") {
        return jsonResponse(200, config);
      }
      return submit(url, init?.body ? JSON.parse(String(init.body)) : null);
    }),
  );
  return calls;
}
