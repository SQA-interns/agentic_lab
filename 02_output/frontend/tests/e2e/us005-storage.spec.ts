// US-005 Reliable registration storage, end to end: data survives container recreation.
import { expect, test } from "@playwright/test";
import { fillExternal, organizerAuth, uniqueEmail } from "./helpers";
import { compose, loadState } from "./stack";

test("AC-005-04 registrations survive recreating every container", async ({ page, request }) => {
  const email = uniqueEmail("e2e-restart");
  await fillExternal(page, email, "Vztrajna", "Zapisnica");
  await page.getByTestId("submit").click();
  await expect(page.getByTestId("confirmation")).toBeVisible();
  const before = await request.get("/api/export", { headers: { Authorization: organizerAuth() } });
  expect(before.status()).toBe(200);

  const state = loadState();
  compose(state, "down");
  compose(state, "up", "-d", "--wait", "--wait-timeout", "300");

  const after = await request.get("/api/export", { headers: { Authorization: organizerAuth() } });
  expect(after.status()).toBe(200);
  expect((await after.body()).length).toBeGreaterThanOrEqual((await before.body()).length);

  await fillExternal(page, email, "Vztrajna", "Zapisnica");
  await page.getByTestId("submit").click();
  await expect(page.getByTestId("form-error")).toHaveText(
    "This email address is already registered.",
  );
});
