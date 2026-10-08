import { afterEach, describe, expect, it, vi } from "vitest";
import { GENERIC_FAILURE, loadForm, submitRegistration, type RegistrationRequest } from "./api";

const request: RegistrationRequest = {
  type: "EXTERNAL",
  firstName: "A",
  lastName: "B",
  email: "a@b.si",
  organization: "O",
  optionIds: [],
  consentIds: ["data"],
  recaptchaToken: "t",
};

function respond(status: number, body: string, contentType = "application/json") {
  const fetchMock = vi.fn(
    async () => new Response(body, { status, headers: { "Content-Type": contentType } }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("submitRegistration", () => {
  it("posts JSON to the relative API path (AR-01)", async () => {
    const fetchMock = respond(201, '{"id":"x","receivedAt":"t"}');
    expect(await submitRegistration(request)).toEqual({ kind: "accepted", id: "x" });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual(request);
  });

  it("returns field errors of a 400", async () => {
    respond(
      400,
      '{"title":"Invalid","status":400,"errors":[{"field":"email","code":"invalid_email"}]}',
    );
    expect(await submitRegistration(request)).toEqual({
      kind: "invalid",
      errors: [{ field: "email", code: "invalid_email" }],
    });
  });

  it("shows the problem title for 409 and 429", async () => {
    respond(409, '{"title":"This email address is already registered.","status":409}');
    expect(await submitRegistration(request)).toEqual({
      kind: "failed",
      title: "This email address is already registered.",
    });
  });

  it("never shows a server error text (SB-07)", async () => {
    respond(500, '{"title":"java.lang.NullPointerException at X","status":500}');
    expect(await submitRegistration(request)).toEqual({ kind: "failed", title: GENERIC_FAILURE });
  });

  it("handles bodies that are not JSON and network failures", async () => {
    respond(502, "<html>Bad gateway</html>", "text/html");
    expect(await submitRegistration(request)).toEqual({ kind: "failed", title: GENERIC_FAILURE });
    respond(400, "not json");
    expect(await submitRegistration(request)).toEqual({ kind: "failed", title: GENERIC_FAILURE });
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("offline"))),
    );
    expect(await submitRegistration(request)).toEqual({ kind: "failed", title: GENERIC_FAILURE });
  });
});

describe("loadForm", () => {
  it("fails when the configuration cannot be loaded", async () => {
    respond(503, "{}");
    await expect(loadForm()).rejects.toThrow("form configuration unavailable (503)");
  });
});
