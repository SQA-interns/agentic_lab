import { expect, test } from "@playwright/test";
import { chooseType, confirmAndSubmit, openForm } from "./support";

test("AC-003-01 AC-003-02 active options are grouped by category and inactive ones are hidden", async ({
  page,
}) => {
  await openForm(page);

  await expect(page.getByRole("group", { name: "Workshops" }).getByRole("checkbox")).toHaveCount(3);
  await expect(page.getByRole("group", { name: "Events" }).getByRole("checkbox")).toHaveCount(2);
  await expect(page.getByRole("group", { name: "Meals" }).getByRole("checkbox")).toHaveCount(3);
  await expect(
    page
      .getByRole("group", { name: "Other activities" })
      .getByRole("checkbox", { name: "Ljubljana city tour" }),
  ).toBeVisible();
  await expect(page.getByRole("checkbox", { name: "Workshop: Retired session" })).toHaveCount(0);
});

test("AC-003-04 options for external participants only are not offered to students", async ({
  page,
}) => {
  await openForm(page);
  await chooseType(page, "Student");

  await expect(page.getByRole("checkbox", { name: "Gala dinner" })).toHaveCount(0);
  await expect(page.getByRole("checkbox", { name: "Workshop: Industry lab" })).toHaveCount(0);
  await expect(page.getByRole("checkbox", { name: "Welcome reception" })).toBeVisible();
});

test("AC-004-02 AC-001-15 invalid input shows errors next to the fields and keeps the values", async ({
  page,
}) => {
  await openForm(page);
  await page.getByLabel("First name").fill("Janez");
  await page.getByLabel("Email").fill("not-an-email");

  await confirmAndSubmit(page);

  await expect(page.getByLabel("Email")).toHaveAccessibleDescription(
    "Enter a valid email address.",
  );
  await expect(page.getByLabel("Last name")).toHaveAccessibleDescription("This field is required.");
  await expect(page.getByLabel("First name")).toHaveValue("Janez");
  await expect(page.getByRole("status")).toHaveCount(0);
});
