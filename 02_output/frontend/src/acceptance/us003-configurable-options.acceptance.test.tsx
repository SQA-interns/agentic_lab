// US-003 Configurable conference options, as the form shows what the options contract returns.
import "@testing-library/jest-dom/vitest";
import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  ACTIVE_OPTIONS,
  MESSAGES,
  type Option,
  chooseType,
  fillExternalFields,
  giveConsentAndPassCaptcha,
  installBackend,
  offeredOptionNames,
  openRegistrationPage,
  optionsInGroup,
  submit,
  tick,
} from "./harness";

const CHANGED_OPTIONS: Option[] = [
  { id: "ws-testing", name: "Delavnica: testiranje programske opreme", category: "workshop" },
  { id: "ws-new", name: "Delavnica: umetna inteligenca", category: "workshop" },
  { id: "ev-opening", name: "Otvoritev v novi dvorani", category: "event" },
  { id: "meal-lunch-day1", name: "Kosilo, prvi dan", category: "meal" },
];
const CHANGED_NAMES = CHANGED_OPTIONS.map((option) => option.name);

describe("US-003 configurable conference options", () => {
  it("AC-003-01 offers the active options grouped by category with their display names", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("EXTERNAL");

    expect(optionsInGroup("Delavnice")).toEqual([
      "Delavnica: testiranje programske opreme",
      "Delavnica: varnost spletnih aplikacij",
    ]);
    expect(optionsInGroup("Dogodki")).toEqual(["Otvoritvena slovesnost", "Slavnostna večerja"]);
    expect(optionsInGroup("Obroki")).toEqual(["Kosilo, prvi dan", "Vegetarijanski meni"]);
    expect(optionsInGroup("Druge aktivnosti")).toEqual(["Voden ogled mesta"]);
  });

  it("AC-003-02 does not offer an option that is not active", async () => {
    installBackend();
    await openRegistrationPage();

    chooseType("EXTERNAL");

    expect(offeredOptionNames()).toHaveLength(ACTIVE_OPTIONS.length);
    expect(screen.queryByRole("checkbox", { name: "Delavnica: preteklo leto" })).toBeNull();
  });

  it("AC-003-03 offers the new set of options and keeps the fixed fields", async () => {
    installBackend({ options: CHANGED_OPTIONS });
    await openRegistrationPage();

    chooseType("EXTERNAL");

    expect(offeredOptionNames(CHANGED_NAMES)).toEqual(CHANGED_NAMES);
    expect(screen.queryByRole("checkbox", { name: "Otvoritvena slovesnost" })).toBeNull();
    expect(screen.getAllByRole("textbox")).toHaveLength(4);
    chooseType("STUDENT");
    expect(offeredOptionNames(CHANGED_NAMES)).toEqual(CHANGED_NAMES);
    expect(screen.getAllByRole("textbox")).toHaveLength(6);
  });

  it("AC-003-05 stays usable when a category has no active option", async () => {
    const backend = installBackend({ options: CHANGED_OPTIONS });
    await openRegistrationPage();
    chooseType("EXTERNAL");
    expect(screen.queryByRole("group", { name: "Druge aktivnosti" })).toBeNull();
    fillExternalFields();
    tick("Delavnica: umetna inteligenca");
    giveConsentAndPassCaptcha();

    submit();

    expect(await screen.findByText(MESSAGES.confirmationHeading)).toBeVisible();
    expect(backend.registrations()[0]?.body?.optionIds).toEqual(["ws-new"]);
  });
});
