import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchOptions,
  submitRegistration,
  type RegistrationRequest,
} from "./client";

const request: RegistrationRequest = {
  type: "EXTERNAL",
  firstName: "Ana",
  lastName: "Novak",
  email: "a@x.si",
  organization: "IJS",
  optionIds: [],
  personalDataConsent: true,
  recaptchaToken: "test-pass",
};

function mockFetch(response: Response | Error) {
  const fn = vi.fn(() =>
    response instanceof Error
      ? Promise.reject(response)
      : Promise.resolve(response),
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("submitRegistration", () => {
  it("returns the registration on 201 and posts JSON", async () => {
    const fetchMock = mockFetch(
      new Response(JSON.stringify({ registrationId: "id-1", options: [] }), {
        status: 201,
      }),
    );

    const result = await submitRegistration(request);

    expect(result).toMatchObject({
      kind: "created",
      registration: { registrationId: "id-1" },
    });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [
      string,
      RequestInit,
    ];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual(request);
  });

  it("returns field errors on 400", async () => {
    mockFetch(
      new Response(
        JSON.stringify({
          errors: [{ field: "email", code: "INVALID_EMAIL", message: "m" }],
        }),
        { status: 400 },
      ),
    );

    expect(await submitRegistration(request)).toEqual({
      kind: "rejected",
      errors: [{ field: "email", code: "INVALID_EMAIL", message: "m" }],
    });
  });

  it.each([
    new Response("{}", { status: 400 }),
    new Response("not json", { status: 400 }),
    new Response("{}", { status: 500 }),
    new Response("{}", { status: 429 }),
    new Error("network down"),
  ])("treats anything else as a general failure", async (response) => {
    mockFetch(response);

    expect(await submitRegistration(request)).toEqual({ kind: "failed" });
  });
});

describe("fetchOptions", () => {
  it("throws on a non-OK response", async () => {
    mockFetch(new Response("{}", { status: 503 }));

    await expect(fetchOptions()).rejects.toThrow("503");
  });
});
