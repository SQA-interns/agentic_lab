import { GENERIC_ERROR, fetchConfig, fetchOptions, submitRegistration } from "./api";

function mockFetch(status: number, body?: unknown) {
  const fn = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("reading", () => {
  it("loads config and options from /api", async () => {
    const fetchFn = mockFetch(200, { recaptchaSiteKey: "", recaptchaTestMode: true });
    await expect(fetchConfig()).resolves.toEqual({ recaptchaSiteKey: "", recaptchaTestMode: true });
    expect(fetchFn).toHaveBeenCalledWith("/api/config", expect.anything());

    mockFetch(200, { options: [], consents: [] });
    await expect(fetchOptions()).resolves.toEqual({ options: [], consents: [] });
  });

  it("fails on an error status", async () => {
    mockFetch(500, {});
    await expect(fetchOptions()).rejects.toThrow("GET /api/options failed with 500");
  });
});

describe("submitRegistration", () => {
  it("posts JSON and returns the registration on 201", async () => {
    const fetchFn = mockFetch(201, { id: "1", firstName: "Ana" });

    const result = await submitRegistration({ type: "EXTERNAL" });

    expect(result).toEqual({ ok: true, registration: { id: "1", firstName: "Ana" } });
    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(init.body)).toEqual({ type: "EXTERNAL" });
  });

  it("maps field errors of 400 and 409, first message per field", async () => {
    mockFetch(409, {
      fieldErrors: [
        { field: "email", message: "Exists." },
        { field: "email", message: "Second." },
      ],
    });

    await expect(submitRegistration({})).resolves.toEqual({
      ok: false,
      fieldErrors: { email: "Exists." },
    });
  });

  it("uses a generic message for 400 without field errors", async () => {
    mockFetch(400, { message: "bad" });

    await expect(submitRegistration({})).resolves.toEqual({
      ok: false,
      fieldErrors: {},
      message: GENERIC_ERROR,
    });
  });

  it("explains rate limiting", async () => {
    mockFetch(429, {});

    const result = await submitRegistration({});

    expect(result.ok).toBe(false);
    expect(!result.ok && result.message).toContain("Too many attempts");
  });

  it.each([413, 500, 503])("uses a generic message for %s", async (status) => {
    mockFetch(status, {});

    await expect(submitRegistration({})).resolves.toEqual({
      ok: false,
      fieldErrors: {},
      message: GENERIC_ERROR,
    });
  });

  it("handles network failures and unreadable error bodies", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    await expect(submitRegistration({})).resolves.toEqual({
      ok: false,
      fieldErrors: {},
      message: GENERIC_ERROR,
    });

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("not json", { status: 400 })));
    await expect(submitRegistration({})).resolves.toEqual({
      ok: false,
      fieldErrors: {},
      message: GENERIC_ERROR,
    });
  });
});
