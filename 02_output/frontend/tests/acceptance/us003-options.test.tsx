// US-003 Configurable conference options: what the form shows from the configuration.
import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "../../src/App";
import { accepted, chooseType, formLoaded, mockApi } from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

function group(legend: string): HTMLElement {
  return screen.getByRole("group", { name: legend });
}

describe("US-003 configurable conference options", () => {
  it("AC-003-01 shows the options grouped by category with their display names", async () => {
    mockApi(accepted());
    render(<App />);
    await formLoaded();

    expect(
      within(group("Workshops")).getByRole("checkbox", {
        name: "Delavnica: testiranje",
      }),
    ).toBeVisible();
    expect(
      within(group("Events")).getByRole("checkbox", {
        name: "Conference dinner",
      }),
    ).toBeVisible();
    expect(
      within(group("Meals")).getByRole("checkbox", { name: "Kosilo" }),
    ).toBeVisible();
    expect(
      within(group("Other activities")).getByRole("checkbox", {
        name: "City tour",
      }),
    ).toBeVisible();
    expect(
      within(group("Workshops")).queryByRole("checkbox", { name: "Kosilo" }),
    ).toBeNull();
  });

  it("AC-003-02 shows only the options the configuration offers and none unchecked by default", async () => {
    mockApi(accepted());
    render(<App />);
    await formLoaded();

    expect(
      screen.queryByRole("checkbox", { name: "Cancelled workshop" }),
    ).toBeNull();
    for (const name of [
      "Delavnica: testiranje",
      "Conference dinner",
      "Kosilo",
      "City tour",
    ]) {
      expect(screen.getByRole("checkbox", { name })).not.toBeChecked();
    }
  });

  it("AC-003-03 shows an external-only option on the external form and not on the student form", async () => {
    mockApi(accepted());
    render(<App />);
    await formLoaded();

    expect(
      screen.getByRole("checkbox", { name: "Industry workshop" }),
    ).toBeVisible();

    chooseType("Student");

    expect(
      screen.queryByRole("checkbox", { name: "Industry workshop" }),
    ).toBeNull();
    expect(
      screen.getByRole("checkbox", { name: "Delavnica: testiranje" }),
    ).toBeVisible();
  });
});
