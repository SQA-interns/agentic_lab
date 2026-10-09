import { describe, expect, it, vi } from "vitest";
import { loadForm, submitRegistration, type RegistrationRequest } from "./api";
import { externalForm, jsonResponse } from "./test/fixtures";

const request: RegistrationRequest = {
  type: "EXTERNAL",
  values: { firstName: "Ana" },
  optionIds: ["ws-a"],
  consentIds: ["privacy"],
  recaptchaToken: "test-pass",
};

describe("api client (AR-01)", () => {
  it("loads the form of a type from /api", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, externalForm));
    vi.stubGlobal("fetch", fetchMock);

    await expect(loadForm("STUDENT")).resolves.toEqual(externalForm);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/registration-form/student");
  });

  it("fails when the form cannot be loaded", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(503, {})));

    await expect(loadForm("EXTERNAL")).rejects.toThrow();
  });

  it("posts the flat request body", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { id: "1" }));
    vi.stubGlobal("fetch", fetchMock);

    await submitRegistration(request);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/registrations");
    expect(JSON.parse(init.body as string)).toEqual({
      type: "EXTERNAL",
      firstName: "Ana",
      optionIds: ["ws-a"],
      consentIds: ["privacy"],
      recaptchaToken: "test-pass",
    });
  });

  it.each([
    [201, { id: "1" }, "accepted"],
    [400, { errors: [{ field: "email", code: "INVALID_EMAIL" }] }, "rejected"],
    [
      409,
      { errors: [{ field: "email", code: "ALREADY_REGISTERED" }] },
      "rejected",
    ],
    [400, { title: "Bad" }, "failed"],
    [429, {}, "rateLimited"],
    [503, { title: "x" }, "failed"],
  ])("maps status %i to %s", async (status, body, kind) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(status, body)),
    );

    expect((await submitRegistration(request)).kind).toBe(kind);
  });

  it("AC-004-03 maps a network error to a general failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));

    expect((await submitRegistration(request)).kind).toBe("failed");
  });
});
