import { afterEach, describe, expect, it, vi } from "vitest";
import { loadRegistrationForm, submitRegistration } from "./api";

function reply(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(typeof body === "string" ? body : JSON.stringify(body), {
          status,
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
}

const request = {
  type: "EXTERNAL" as const,
  firstName: "Ana",
  optionIds: [],
  consentIds: [],
  captchaToken: "t",
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api client", () => {
  it("posts JSON to the relative registration endpoint", async () => {
    reply(201, { registrationId: "id", registeredAt: "t", type: "EXTERNAL" });

    const result = await submitRegistration(request);

    expect(result).toEqual({
      kind: "created",
      created: { registrationId: "id", registeredAt: "t", type: "EXTERNAL" },
    });
    const [url, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(url).toBe("/api/registrations");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual(request);
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });

  it("returns field errors of 400 and 409 as a rejection", async () => {
    const error = {
      error: "duplicate_email",
      message: "m",
      fieldErrors: [{ field: "email", code: "duplicate_email", message: "m" }],
    };
    reply(409, error);

    expect(await submitRegistration(request)).toEqual({ kind: "rejected", error });
  });

  it("returns the error code of other failures", async () => {
    reply(400, { error: "captcha_failed", message: "m", fieldErrors: [] });
    expect(await submitRegistration(request)).toEqual({
      kind: "failed",
      errorCode: "captcha_failed",
    });

    reply(429, { error: "rate_limited", message: "m", fieldErrors: [] });
    expect(await submitRegistration(request)).toEqual({
      kind: "failed",
      errorCode: "rate_limited",
    });

    reply(500, { error: "internal_error", message: "m", fieldErrors: [] });
    expect(await submitRegistration(request)).toEqual({
      kind: "failed",
      errorCode: "internal_error",
    });
  });

  it("treats unreadable answers and network failures as failed without a code", async () => {
    reply(502, "<html>Bad Gateway</html>");
    expect(await submitRegistration(request)).toEqual({ kind: "failed", errorCode: null });

    reply(201, { unexpected: true });
    expect(await submitRegistration(request)).toEqual({ kind: "failed", errorCode: null });

    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("network");
      }),
    );
    expect(await submitRegistration(request)).toEqual({ kind: "failed", errorCode: null });
  });

  it("loads the form data and fails on an error status", async () => {
    reply(200, {
      conferenceName: "C",
      captcha: { mode: "test", siteKey: null },
      categories: [],
      consents: [],
    });
    expect((await loadRegistrationForm()).conferenceName).toBe("C");
    expect(vi.mocked(fetch).mock.calls[0]![0]).toBe("/api/registration-form");

    reply(503, {});
    await expect(loadRegistrationForm()).rejects.toThrow("503");
  });
});
