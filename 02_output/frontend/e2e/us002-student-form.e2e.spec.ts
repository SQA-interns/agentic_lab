import { expect, test } from "@playwright/test";
import { fetchOptions, openForm } from "./support";

// US-002 student registration through the UI.

test("AC-002-03 the student form shows exactly its six fields and unchecked consents", async ({
  page,
  request,
}) => {
  const { consents } = await fetchOptions(request);
  await openForm(page);
  await page.getByLabel("Student", { exact: true }).check();

  const details = page.getByRole("group", { name: "Your details" });
  const labels = await details.locator("label").allTextContents();
  expect(labels.map((l) => l.trim())).toEqual([
    "First name",
    "Last name",
    "Email",
    "Study institution",
    "Study programme",
    "Student ID",
  ]);
  await expect(details.getByLabel("Organization / institution")).toHaveCount(0);

  const boxes = page.getByRole("group", { name: "Consents" }).getByRole("checkbox");
  await expect(boxes).toHaveCount(consents.length);
  for (let i = 0; i < consents.length; i++) {
    await expect(boxes.nth(i)).not.toBeChecked();
  }
});

test("AC-002-03 switching type keeps the common fields", async ({ page }) => {
  await openForm(page);
  await page.getByLabel("External participant", { exact: true }).check();
  await page.getByLabel("First name", { exact: true }).fill("Luka");
  await page.getByLabel("Email", { exact: true }).fill("luka@example.si");
  await page.getByLabel("Student", { exact: true }).check();

  await expect(page.getByLabel("First name", { exact: true })).toHaveValue("Luka");
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue("luka@example.si");
  await expect(page.getByLabel("Student ID", { exact: true })).toBeVisible();
});
