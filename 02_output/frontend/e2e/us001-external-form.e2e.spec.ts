import { expect, test } from "@playwright/test";
import {
  checkRequiredConsents,
  fetchOptions,
  fillExternal,
  mailpitPart,
  openForm,
  uniqueEmail,
  waitForMessages,
  xlsxStrings,
} from "./support";

// US-001 external participant registration through the UI.

test("AC-001-12 the external form shows exactly its four fields and unchecked consents", async ({
  page,
  request,
}) => {
  const { consents } = await fetchOptions(request);
  await openForm(page);
  await page.getByLabel("External participant", { exact: true }).check();

  const details = page.getByRole("group", { name: "Your details" });
  const labels = await details.locator("label").allTextContents();
  expect(labels.map((l) => l.trim())).toEqual([
    "First name",
    "Last name",
    "Email",
    "Organization / institution",
  ]);
  await expect(details.getByLabel("Study institution")).toHaveCount(0);
  await expect(details.getByLabel("Student ID")).toHaveCount(0);

  const consentGroup = page.getByRole("group", { name: "Consents" });
  const boxes = consentGroup.getByRole("checkbox");
  await expect(boxes).toHaveCount(consents.length);
  for (let i = 0; i < consents.length; i++) {
    await expect(boxes.nth(i)).not.toBeChecked();
  }
});

test("AC-001-13 NFR-01 Slovenian characters survive form, emails, JSON copy and export", async ({
  page,
  request,
}) => {
  const username = process.env.E2E_ORGANIZER_USERNAME;
  const password = process.env.E2E_ORGANIZER_PASSWORD;
  expect(username, "E2E_ORGANIZER_USERNAME must be set").toBeTruthy();
  expect(password, "E2E_ORGANIZER_PASSWORD must be set").toBeTruthy();

  const { consents } = await fetchOptions(request);
  const email = uniqueEmail("sl");
  const firstName = "Čedomira Žana";
  const lastName = "Šuštaršič Čater";
  const organization = "Društvo študentov ŠČŽ čšž";

  await openForm(page);
  await fillExternal(page, { firstName, lastName, email, organization });
  await checkRequiredConsents(page, consents);
  await page.getByRole("button", { name: "Register" }).click();

  await expect(page.getByRole("heading", { name: "Registration received" })).toBeVisible();
  await expect(page.getByText(`${firstName} ${lastName}`, { exact: false })).toBeVisible();

  const participant = await waitForMessages(`to:"${email}"`);
  expect(participant[0].Text).toContain(firstName);
  expect(participant[0].Text).toContain(lastName);

  const organizer = await waitForMessages(`"${email}" subject:"New registration"`);
  const attachment = organizer[0].Attachments.find((a) => a.FileName.endsWith(".json"));
  expect(attachment).toBeDefined();
  const copy = JSON.parse(await mailpitPart(organizer[0].ID, attachment!.PartID));
  expect(copy.firstName).toBe(firstName);
  expect(copy.lastName).toBe(lastName);
  expect(copy.organization).toBe(organization);

  const exported = await request.get("/api/organizer/registrations.xlsx", {
    headers: {
      Authorization: `Basic ${btoa(`${username}:${password}`)}`,
    },
  });
  expect(exported.status()).toBe(200);
  const strings = await xlsxStrings(new Uint8Array(await exported.body()));
  expect(strings).toContain(firstName);
  expect(strings).toContain(lastName);
  expect(strings).toContain(organization);
});
