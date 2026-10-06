import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchSetup, submitRegistration } from "./client";
import type { RegistrationRequest } from "./types";

const REQUEST: RegistrationRequest = {
  type: "EXTERNAL",
  firstName: "Ana",
  lastName: "Novak",
  email: "a@b.si",
  organization: "IJS",
  optionIds: [],
  consentGiven: true,
  recaptchaToken: "t",
};

function reply(status: number, body: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => new Response(body, { status })),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("submitRegistration", () => {
  it("posts JSON to the relative API path (AR-01)", async () => {
    const f = vi.fn(
      async () => new Response('{"registrationId":"x","receivedAt":"y"}', { status: 201 }),
    );
    vi.stubGlobal("fetch", f);

    expect(await submitRegistration(REQUEST)).toEqual({
      kind: "accepted",
      registrationId: "x",
      receivedAt: "y",
    });
    expect(f).toHaveBeenCalledWith(
      "/api/registrations",
      expect.objectContaining({ method: "POST", body: JSON.stringify(REQUEST) }),
    );
  });

  it.each([
    [409, "{}", "duplicate"],
    [400, '{"errors":[]}', "failed"],
    [400, "{}", "failed"],
    [400, "not json", "failed"],
    [201, "not json", "failed"],
    [413, "{}", "failed"],
    [429, "{}", "failed"],
    [500, "{}", "failed"],
  ])("maps status %i with body %s to %s", async (status, body, kind) => {
    reply(status, body);
    expect((await submitRegistration(REQUEST)).kind).toBe(kind);
  });

  it("returns field errors of a 400", async () => {
    reply(400, '{"errors":[{"field":"email","code":"INVALID_EMAIL"}]}');
    expect(await submitRegistration(REQUEST)).toEqual({
      kind: "invalid",
      errors: [{ field: "email", code: "INVALID_EMAIL" }],
    });
  });

  it("treats a network failure as failed", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("offline"))),
    );
    expect((await submitRegistration(REQUEST)).kind).toBe("failed");
  });
});

describe("fetchSetup", () => {
  it("rejects when the server answers with an error", async () => {
    reply(503, "{}");
    await expect(fetchSetup()).rejects.toThrow("503");
  });
});
