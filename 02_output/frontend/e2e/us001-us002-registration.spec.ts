import { expect, test } from "@playwright/test";
import {
  chooseType,
  confirmAndSubmit,
  expectConfirmation,
  openForm,
  registerExternal,
  uniqueEmail,
} from "./support";

test("AC-001-02 AC-001-07 an external participant with Slovenian characters registers", async ({
  page,
}) => {
  const email = uniqueEmail("ziga.cebasek");

  await registerExternal(
    page,
    {
      firstName: "Žiga",
      lastName: "Čebašek",
      email,
      organization: "Fakulteta za računalništvo, Šiška",
    },
    ["Workshop: AI in research", "Lunch, day 1"],
  );

  await expectConfirmation(page, "Žiga", email);
});

test("AC-001-14 a second registration with the same email is refused with a message", async ({
  page,
}) => {
  const email = uniqueEmail("janez.novak");
  const person = { firstName: "Janez", lastName: "Novak", email, organization: "Institute" };
  await registerExternal(page, person);
  await expectConfirmation(page, "Janez", email);

  await registerExternal(page, { ...person, email: email.toUpperCase() });

  await expect(page.getByRole("alert")).toContainText(/organizer/i);
  await expect(page.getByRole("status")).toHaveCount(0);
});

test("AC-002-02 a student registers", async ({ page }) => {
  const email = uniqueEmail("spela.zagar");
  await openForm(page);
  await chooseType(page, "Student");
  await page.getByLabel("First name").fill("Špela");
  await page.getByLabel("Last name").fill("Žagar");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Study institution").fill("Univerza v Ljubljani");
  await page.getByLabel("Study programme").fill("Računalništvo in informatika");
  await page.getByLabel("Student ID").fill("63210001");
  await page.getByRole("checkbox", { name: "Workshop: Open data" }).check();

  await confirmAndSubmit(page);

  await expectConfirmation(page, "Špela", email);
});
