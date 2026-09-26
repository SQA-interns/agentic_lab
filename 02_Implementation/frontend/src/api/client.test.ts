import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchConference, submitRegistration } from "./client";

const request = {
  type: "EXTERNAL" as const,
  firstName: "Ana",
  optionIds: [],
  consentIds: ["privacy"],
  recaptchaToken: "t",
};

function mockFetch(response: Response | Error) {
  const fn =
    response instanceof Error
      ? vi.fn().mockRejectedValue(response)
      : vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("submitRegistration", () => {
  it("posts JSON and returns success on 201", async () => {
    const fetchMock = mockFetch(
      new Response(
        JSON.stringify({ registrationId: "id-1", type: "EXTERNAL" }),
        {
          status: 201,
        },
      ),
    );
    const result = await submitRegistration(request);
    expect(result).toEqual({
      ok: true,
      data: { registrationId: "id-1", type: "EXTERNAL" },
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(init.body)).toEqual(request);
  });

  it("returns field errors from a 400 response", async () => {
    mockFetch(
      new Response(
        JSON.stringify({
          code: "VALIDATION_FAILED",
          message: "Fix it",
          fieldErrors: [{ field: "email", message: "Bad" }],
        }),
        { status: 400 },
      ),
    );
    const result = await submitRegistration(request);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(400);
      expect(result.error.fieldErrors).toEqual([
        { field: "email", message: "Bad" },
      ]);
    }
  });

  it("treats a 200 response as not confirmed", async () => {
    mockFetch(new Response("{}", { status: 200 }));
    const result = await submitRegistration(request);
    expect(result.ok).toBe(false);
  });

  it("handles non-JSON error bodies", async () => {
    mockFetch(new Response("<html>502</html>", { status: 502 }));
    const result = await submitRegistration(request);
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error.code).toBe("UNKNOWN_ERROR");
      expect(result.error.fieldErrors).toEqual([]);
    }
  });

  it("handles network failure", async () => {
    mockFetch(new TypeError("offline"));
    const result = await submitRegistration(request);
    expect(result).toMatchObject({ ok: false, status: 0 });
  });
});

describe("fetchConference", () => {
  it("returns the configuration", async () => {
    const config = {
      options: [],
      consents: [],
      recaptcha: { siteKey: "", testMode: true },
    };
    mockFetch(new Response(JSON.stringify(config), { status: 200 }));
    await expect(fetchConference()).resolves.toEqual(config);
  });

  it("throws on error status", async () => {
    mockFetch(new Response("", { status: 500 }));
    await expect(fetchConference()).rejects.toThrow();
  });
});
