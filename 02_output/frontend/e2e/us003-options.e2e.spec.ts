import { expect, test } from "@playwright/test";
import { openForm, ui } from "./support/form";

test.describe("US-003 Configurable conference options", () => {
  test("AC-003-01 configured options appear under their category headings", async ({
    page,
  }) => {
    await openForm(page, "EXTERNAL");

    await expect(page.getByTestId(ui.category("WORKSHOP"))).toContainText(
      "Delavnica: testiranje sistemov UI",
    );
    await expect(page.getByTestId(ui.category("EVENT"))).toContainText(
      "Gala večerja",
    );
    await expect(page.getByTestId(ui.category("MEAL"))).toContainText("Meals");
    await expect(page.getByTestId(ui.category("OTHER"))).toContainText(
      "Other activities",
    );
  });

  test("AC-003-04 each configured consent is shown with its wording, unticked", async ({
    page,
  }) => {
    await openForm(page, "STUDENT");

    const consent = page.getByTestId(ui.consent("data-processing"));
    await expect(consent).not.toBeChecked();
    await expect(
      page.getByLabel(
        "I agree that the organizer processes my personal data to organise the conference.",
      ),
    ).toBeVisible();
  });
});
