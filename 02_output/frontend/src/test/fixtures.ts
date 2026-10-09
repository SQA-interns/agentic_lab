import type { FormDefinition } from "../api";

export const externalForm: FormDefinition = {
  type: "EXTERNAL",
  fields: [
    { name: "firstName", maxLength: 100 },
    { name: "lastName", maxLength: 100 },
    { name: "email", maxLength: 254 },
    { name: "organization", maxLength: 200 },
  ],
  categories: [
    {
      category: "WORKSHOP",
      maxSelections: 1,
      options: [
        { id: "ws-a", name: "Workshop A" },
        { id: "ws-b", name: "Workshop B" },
      ],
    },
    {
      category: "EVENT",
      maxSelections: 1,
      options: [{ id: "ev-a", name: "Gala večerja" }],
    },
    { category: "MEAL", maxSelections: 2, options: [] },
    { category: "OTHER", maxSelections: 1, options: [] },
  ],
  consents: [
    { id: "privacy", text: "I agree to processing.", mandatory: true },
    { id: "photo", text: "Photos are fine.", mandatory: false },
  ],
  recaptcha: { mode: "TEST" },
};

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
