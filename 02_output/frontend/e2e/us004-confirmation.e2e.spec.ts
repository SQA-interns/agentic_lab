import { expect, test } from "@playwright/test";
import {
  TEST_MODE_TOKEN,
  checkRequiredConsents,
  fetchOptions,
  fillExternal,
  openForm,
  uniqueEmail,
} from "./support";

// US-004 confirmation after acceptance, and error display (NFR-03).

test("AC-004-01 an accepted registration replaces the form with a confirmation", async ({
  page,
  request,
}) => {
  const { options, consents } = await fetchOptions(request);
  const chosen = options.slice(0, 2);
  const email = uniqueEmail("confirm");

  await openForm(page);
  await fillExternal(page, {
    firstName: "Maja",
    lastName: "Horvat",
    email,
    organization: "Zavod Test",
  });
  for (const o of chosen) {
    await page.getByRole("group", { name: "Options" }).getByLabel(o.name, { exact: true }).check();
  }
  await checkRequiredConsents(page, consents);
  await page.getByRole("button", { name: "Register" }).click();

  await expect(page.getByRole("heading", { name: "Registration received" })).toBeVisible();
  await expect(page.getByText("Thank you, Maja Horvat.", { exact: false })).toBeVisible();
  const list = page.getByRole("list", { name: "Selected options" });
  for (const o of chosen) {
    await expect(list.getByText(o.name, { exact: true })).toBeVisible();
  }
  await expect(page.getByText(`A confirmation email has been sent to ${email}.`)).toBeVisible();
  await expect(page.getByRole("button", { name: "Register" })).toHaveCount(0);
});

test("AC-004-02 a rejected registration shows the field error and keeps the values", async ({
  page,
  request,
}) => {
  const { consents } = await fetchOptions(request);
  const email = uniqueEmail("dup");
  const first = await request.post("/api/registrations", {
    headers: { "X-Forwarded-For": "10.250.0.1" },
    data: {
      type: "EXTERNAL",
      firstName: "Prvi",
      lastName: "Udeleženec",
      email,
      organization: "Org",
      optionIds: [],
      consents: consents.filter((c) => c.required).map((c) => c.id),
      recaptchaToken: TEST_MODE_TOKEN,
    },
  });
  expect(first.status()).toBe(201);

  await openForm(page);
  await fillExternal(page, {
    firstName: "Drugi",
    lastName: "Poskus",
    email,
    organization: "Org 2",
  });
  await checkRequiredConsents(page, consents);
  await page.getByRole("button", { name: "Register" }).click();

  const error = page.locator("#email-error");
  await expect(error).toBeVisible();
  await expect(error).not.toBeEmpty();
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute(
    "aria-describedby",
    /email-error/,
  );
  await expect(page.getByRole("heading", { name: "Registration received" })).toHaveCount(0);
  await expect(page.getByLabel("First name", { exact: true })).toHaveValue("Drugi");
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue(email);
});

test("AC-004-03 invalid input is reported next to the fields and nothing is sent", async ({
  page,
}) => {
  const posts: string[] = [];
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().includes("/api/registrations")) posts.push(r.url());
  });

  await openForm(page);
  await page.getByLabel("External participant", { exact: true }).check();
  await page.getByLabel("First name", { exact: true }).fill("   ");
  await page.getByLabel("Email", { exact: true }).fill("not-an-email");
  await page.getByRole("button", { name: "Register" }).click();

  await expect(page.locator("#firstName-error")).toHaveText("This field is required.");
  await expect(page.locator("#lastName-error")).toHaveText("This field is required.");
  await expect(page.locator("#organization-error")).toHaveText("This field is required.");
  await expect(page.locator("#email-error")).toHaveText("Enter a valid email address.");
  await expect(page.locator("#consents-error")).toHaveText("This consent is required.");
  await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute(
    "aria-describedby",
    /email-error/,
  );
  expect(posts).toEqual([]);
});
