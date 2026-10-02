// Unit tests of the page states the acceptance tests do not reach: loading and load failure.
import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const CONFIG = {
  conferenceName: "Konferenca 2027",
  consent: { id: "personal-data", text: "Soglašam." },
  captcha: { mode: "test", siteKey: "" },
};

describe("App", () => {
  it("shows the title at once and the form with the conference name after loading", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string) =>
        Promise.resolve(input === "/api/form-config" ? json(CONFIG) : json({ options: [] })),
      ),
    );

    render(<App />);

    expect(screen.getByRole("heading", { level: 1, name: "Prijava na konferenco" })).toBeVisible();
    expect(screen.queryByRole("radiogroup")).toBeNull();
    expect(await screen.findByRole("radiogroup", { name: "Vrsta prijave" })).toBeVisible();
    expect(screen.getByText("Konferenca 2027")).toBeVisible();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it.each([
    ["the options cannot be loaded", () => Promise.resolve(json({ status: 503 }, 503))],
    ["the backend cannot be reached", () => Promise.reject(new TypeError("failed to fetch"))],
  ])("tells the participant when %s", async (_name, optionsAnswer) => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: string) =>
        input === "/api/form-config" ? Promise.resolve(json(CONFIG)) : optionsAnswer(),
      ),
    );

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Obrazca ni bilo mogoče naložiti. Osvežite stran.",
    );
    expect(screen.queryByRole("radiogroup")).toBeNull();
  });
});
