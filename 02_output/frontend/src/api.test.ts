import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchFormConfig,
  submitRegistration,
  type RegistrationRequest,
} from "./api";

const request: RegistrationRequest = {
  type: "external",
  firstName: "Ana",
  lastName: "Novak",
  email: "a@b.si",
  organization: "IJS",
  optionIds: [],
  consents: ["data"],
  captchaToken: "t",
};

function respond(status: number, body: string) {
  const fetchMock = vi.fn(async () => new Response(body, { status }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api", () => {
  it("loads the form configuration from the relative API path", async () => {
    const fetchMock = respond(200, JSON.stringify({ conferenceName: "C" }));
    await expect(fetchFormConfig()).resolves.toEqual({ conferenceName: "C" });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/form-config",
      expect.anything(),
    );
  });

  it("fails when the form configuration cannot be loaded", async () => {
    respond(503, "");
    await expect(fetchFormConfig()).rejects.toThrow(
      "form configuration unavailable (503)",
    );
  });

  it("posts the registration as JSON and returns the confirmation", async () => {
    const fetchMock = respond(201, JSON.stringify({ registrationId: "id" }));
    const result = await submitRegistration(request);
    expect(result).toEqual({
      kind: "accepted",
      confirmation: { registrationId: "id" },
    });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [
      string,
      RequestInit,
    ];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual(request);
  });

  it("keeps only well-formed field errors of a rejection", async () => {
    respond(
      400,
      JSON.stringify({
        detail: "internal detail",
        errors: [
          {
            field: "email",
            code: "INVALID_EMAIL",
            message: "Enter a valid email address.",
          },
          { field: "x" },
          null,
          "text",
        ],
      }),
    );
    expect(await submitRegistration(request)).toEqual({
      kind: "rejected",
      status: 400,
      errors: [
        {
          field: "email",
          code: "INVALID_EMAIL",
          message: "Enter a valid email address.",
        },
      ],
    });
  });

  it.each([
    ["not json", 500],
    [JSON.stringify({ errors: "none" }), 429],
    [JSON.stringify(null), 500],
  ])("returns no field errors for body %s", async (body, status) => {
    respond(status, body);
    expect(await submitRegistration(request)).toEqual({
      kind: "rejected",
      status,
      errors: [],
    });
  });
});
