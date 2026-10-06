import { screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { chooseType, fakeBackend, labelOf, renderApp } from "./support";

afterEach(() => {
  vi.unstubAllGlobals();
});

function optionNames(groupName: string): string[] {
  const group = screen.getByRole("group", { name: groupName });
  return within(group).getAllByRole("checkbox").map(labelOf);
}

describe("US-003 Configurable conference options (UI)", () => {
  it("AC-003-01 active options are offered grouped by category with their display names", async () => {
    fakeBackend();
    await renderApp();

    expect(optionNames("Workshops")).toEqual([
      "Workshop: AI in research",
      "Workshop: Open data",
      "Workshop: Industry lab",
    ]);
    expect(optionNames("Events")).toEqual(["Welcome reception", "Gala dinner"]);
    expect(optionNames("Meals")).toEqual(["Lunch, day 1", "Lunch, day 2", "Vegetarian meals"]);
    expect(optionNames("Other activities")).toEqual(["Ljubljana city tour"]);
    for (const box of screen.getAllByRole("checkbox", { name: /Workshop|Lunch|reception|tour/ })) {
      expect(box).not.toBeChecked();
    }
  });

  it("AC-003-04 options for external participants only are not offered on the student form", async () => {
    fakeBackend();
    await renderApp();

    chooseType("Student");

    expect(optionNames("Workshops")).toEqual(["Workshop: AI in research", "Workshop: Open data"]);
    expect(optionNames("Events")).toEqual(["Welcome reception"]);
    expect(screen.queryByRole("checkbox", { name: "Gala dinner" })).not.toBeInTheDocument();

    chooseType("External participant");

    expect(screen.getByRole("checkbox", { name: "Gala dinner" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Workshop: Industry lab" })).toBeInTheDocument();
  });
});
