// Unit tests of the API client: every answer of the backend becomes a defined result (AR-01).
import { afterEach, describe, expect, it, vi } from "vitest";
import { type RegistrationRequest, loadFormConfig, loadOptions, submitRegistration } from "./api";

const REQUEST: RegistrationRequest = {
  type: "EXTERNAL",
  firstName: "Živa",
  optionIds: ["ws-testing"],
  consent: true,
  captchaToken: "token",
};

function answer(status: number, body?: unknown, contentType = "application/json") {
  const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { "Content-Type": contentType },
      }),
    ),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("submitRegistration", () => {
  it("AR-01 posts the registration as JSON to /api/registrations", async () => {
    const fetchMock = answer(201, { id: "x", acceptedAt: "2026-10-02T10:15:30Z" });

    expect(await submitRegistration(REQUEST)).toEqual({ kind: "accepted" });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/registrations");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Content-Type")).toBe("application/json");
    expect(JSON.parse(init?.body as string)).toEqual(REQUEST);
  });

  it("returns the field errors of a rejection", async () => {
    const errors = [
      { field: "email", code: "invalid_format", message: "Vnesite veljaven e-poštni naslov." },
      { field: "consent", code: "consent_required", message: "Za prijavo je potrebno soglasje." },
    ];
    answer(
      400,
      { type: "about:blank", title: "x", status: 400, errors },
      "application/problem+json",
    );

    expect(await submitRegistration(REQUEST)).toEqual({ kind: "rejected", errors });
  });

  it("drops entries of a rejection that are not field errors", async () => {
    answer(400, { errors: [{ field: "email", code: "required", message: "m" }, "x", null, {}] });

    expect(await submitRegistration(REQUEST)).toEqual({
      kind: "rejected",
      errors: [{ field: "email", code: "required", message: "m" }],
    });
  });

  it.each([
    ["a 400 without field errors", 400, { status: 400 }],
    ["a 400 with an empty error list", 400, { errors: [] }],
    ["a 400 that is not JSON", 400, undefined],
    ["a 413", 413, { status: 413 }],
    ["a 500", 500, { status: 500 }],
    ["a 503", 503, { status: 503 }],
    ["an unexpected 200", 200, {}],
  ])("treats %s as not received", async (_name, status, body) => {
    answer(status, body);

    expect(await submitRegistration(REQUEST)).toEqual({ kind: "notReceived" });
  });

  it("reports too many requests", async () => {
    answer(429, { status: 429 });

    expect(await submitRegistration(REQUEST)).toEqual({ kind: "tooManyRequests" });
  });

  it("treats a network failure as not received", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new TypeError("failed to fetch"))),
    );

    expect(await submitRegistration(REQUEST)).toEqual({ kind: "notReceived" });
  });
});

describe("loading the form", () => {
  it("AR-01 reads the form configuration and the options under /api", async () => {
    const config = {
      conferenceName: "Konferenca",
      consent: { id: "personal-data", text: "Soglašam." },
      captcha: { mode: "test", siteKey: "" },
    };
    const configFetch = answer(200, config);
    expect(await loadFormConfig()).toEqual(config);
    expect(configFetch.mock.calls[0]?.[0]).toBe("/api/form-config");

    const options = [{ id: "a", name: "A", category: "meal" }];
    const optionsFetch = answer(200, { options });
    expect(await loadOptions()).toEqual(options);
    expect(optionsFetch.mock.calls[0]?.[0]).toBe("/api/options");
  });

  it("fails when the backend does not answer with success", async () => {
    answer(503, { status: 503 });

    await expect(loadFormConfig()).rejects.toThrow("503");
    await expect(loadOptions()).rejects.toThrow("503");
  });
});
