import { expect, test } from "@playwright/test";
import { fetchOptions, openForm } from "./support";

// US-003 configured options are offered in the UI, grouped by category.

const HEADINGS: Record<string, string> = {
  workshop: "Workshops",
  event: "Events",
  meal: "Meals",
  other: "Other activities",
};

test("AC-003-04 active options are grouped under the four category headings", async ({
  page,
  request,
}) => {
  const { options } = await fetchOptions(request);
  expect(options.length).toBeGreaterThan(0);
  await openForm(page);

  const optionsGroup = page.getByRole("group", { name: "Options" });
  const headings = (await optionsGroup.getByRole("heading", { level: 2 }).allTextContents()).map(
    (h) => h.trim(),
  );
  const expectedOrder = ["workshop", "event", "meal", "other"]
    .filter((c) => options.some((o) => o.category === c))
    .map((c) => HEADINGS[c]);
  expect(headings).toEqual(expectedOrder);

  for (const category of Object.keys(HEADINGS)) {
    const expected = options.filter((o) => o.category === category).map((o) => o.name);
    if (expected.length === 0) continue;
    const section = optionsGroup.getByRole("region", { name: HEADINGS[category] });
    const labels = (await section.locator("label").allTextContents()).map((l) => l.trim());
    expect(labels).toEqual(expected);
  }

  await expect(optionsGroup.getByRole("checkbox")).toHaveCount(options.length);
});
