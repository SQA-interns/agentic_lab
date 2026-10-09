import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { App } from "../App";
import { stubApi } from "./support/api-stub";
import { chooseType, participantInputs } from "./support/form";

afterEach(() => {
  cleanup();
});

function optionLabels(category: string): string[] {
  const fieldset = screen.getByTestId(`options-${category}`);
  return within(fieldset)
    .getAllByRole("checkbox")
    .map((box) => box.getAttribute("data-testid") ?? "");
}

describe("US-003 Configurable conference options (UI)", () => {
  it("AC-003-01 offers the options of the form data by name, grouped by category", async () => {
    stubApi();
    render(<App />);

    await chooseType("EXTERNAL");

    expect(optionLabels("workshop")).toEqual(["option-ws-ai", "option-ws-security"]);
    expect(optionLabels("event")).toEqual(["option-ev-reception", "option-ev-industry-dinner"]);
    expect(optionLabels("meal")).toEqual(["option-meal-lunch-1", "option-meal-lunch-2"]);
    expect(screen.getByLabelText("Delavnica umetne inteligence")).toBe(
      screen.getByTestId("option-ws-ai"),
    );
    expect(within(screen.getByTestId("options-workshop")).getByText("Workshops")).toBeTruthy();
    expect(within(screen.getByTestId("options-event")).getByText("Events")).toBeTruthy();
    expect(within(screen.getByTestId("options-meal")).getByText("Meals")).toBeTruthy();
    for (const box of screen.getAllByRole("checkbox")) {
      expect((box as HTMLInputElement).checked).toBe(false);
    }
  });

  it("AC-003-03 options not available to the chosen type are not offered", async () => {
    stubApi();
    render(<App />);

    await chooseType("STUDENT");
    expect(screen.queryByTestId("option-ev-industry-dinner")).toBeNull();
    expect(screen.getByTestId("option-other-career-fair")).toBeTruthy();
    expect(within(screen.getByTestId("options-other")).getByText("Other activities")).toBeTruthy();

    await chooseType("EXTERNAL");
    expect(screen.getByTestId("option-ev-industry-dinner")).toBeTruthy();
    expect(screen.queryByTestId("option-other-career-fair")).toBeNull();
    expect(screen.queryByTestId("options-other")).toBeNull();
  });

  it("AC-003-05 the participant fields are exactly those of BR-01 for each type", async () => {
    stubApi();
    render(<App />);

    await chooseType("EXTERNAL");
    expect(participantInputs().map((input) => input.name)).toEqual([
      "firstName",
      "lastName",
      "email",
      "organization",
    ]);

    await chooseType("STUDENT");
    expect(participantInputs().map((input) => input.name)).toEqual([
      "firstName",
      "lastName",
      "email",
      "studyInstitution",
      "studyProgramme",
      "studentId",
    ]);
  });
});
