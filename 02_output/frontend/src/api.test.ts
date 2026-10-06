import { afterEach, describe, expect, it, vi } from "vitest";
import { getFormConfig, submitRegistration, type RegistrationRequest } from "./api";

const body: RegistrationRequest = {
  type: "EXTERNAL",
  firstName: "J",
  lastName: "N",
  email: "a@b.si",
  organization: "O",
  optionIds: [],
  consentIds: ["c"],
  antiAutomationToken: "t",
};

function reply(status: number, text: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () => new Response(text, { status, headers: { "Content-Type": "application/json" } }),
    ),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("api", () => {
  it("throws when the form configuration cannot be loaded", async () => {
    reply(503, "{}");
    await expect(getFormConfig()).rejects.toThrow("HTTP 503");
  });

  it("posts JSON to /api/registrations", async () => {
    reply(201, JSON.stringify({ id: "1", firstName: "J" }));
    const result = await submitRegistration(body);

    expect(result).toEqual({ kind: "accepted", registration: { id: "1", firstName: "J" } });
    const [url, init] = vi.mocked(fetch).mock.calls[0]!;
    expect(url).toBe("/api/registrations");
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Content-Type")).toBe("application/json");
    expect(JSON.parse(String(init?.body))).toEqual(body);
  });

  it("returns the backend error of a rejected registration", async () => {
    reply(409, JSON.stringify({ code: "DUPLICATE_EMAIL", message: "m" }));
    expect(await submitRegistration(body)).toEqual({
      kind: "rejected",
      error: { code: "DUPLICATE_EMAIL", message: "m" },
    });
  });

  it("treats a non-JSON or foreign error body as unreachable", async () => {
    reply(502, "<html>Bad gateway</html>");
    expect(await submitRegistration(body)).toEqual({ kind: "unreachable" });
    reply(500, JSON.stringify({ error: "x" }));
    expect(await submitRegistration(body)).toEqual({ kind: "unreachable" });
    reply(500, JSON.stringify({ code: "INTERNAL_ERROR" }));
    expect(await submitRegistration(body)).toEqual({ kind: "unreachable" });
    reply(500, JSON.stringify({ message: "m" }));
    expect(await submitRegistration(body)).toEqual({ kind: "unreachable" });
  });
});
