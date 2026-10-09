import { expect, test } from "@playwright/test";
import {
  confirmNotARobot,
  fill,
  jsonCopies,
  openForm,
  student,
  submit,
  tick,
  ui,
  uniqueEmail,
} from "./support/form";

test.describe("US-002 Student registration", () => {
  test("AC-002-01 the student form asks for exactly the student fields", async ({
    page,
  }) => {
    await openForm(page, "STUDENT");

    for (const name of [
      "firstName",
      "lastName",
      "email",
      "studyInstitution",
      "studyProgramme",
      "studentId",
    ]) {
      await expect(page.getByTestId(ui.field(name))).toBeVisible();
    }
    await expect(page.getByTestId(ui.field("organization"))).toHaveCount(0);
  });

  test("AC-002-02 AC-004-01 a valid student registration is confirmed and stored", async ({
    page,
  }) => {
    const email = uniqueEmail();
    await openForm(page, "STUDENT");
    await fill(page, student(email));
    await tick(page, ui.option("ws-testing-ai"));
    await tick(page, ui.consent("data-processing"));
    await confirmNotARobot(page);
    await submit(page);

    await expect(page.getByTestId(ui.confirmation)).toContainText(
      "Registration received",
    );
    expect(jsonCopies().some((c) => c.includes(email))).toBe(true);
  });

  test("AC-002-03 an empty student ID is reported next to the field", async ({
    page,
  }) => {
    await openForm(page, "STUDENT");
    await fill(page, { ...student(uniqueEmail()), studentId: " " });
    await tick(page, ui.consent("data-processing"));
    await confirmNotARobot(page);
    await submit(page);

    await expect(page.getByTestId(ui.error("studentId"))).toHaveText(
      ui.messages.REQUIRED,
    );
    await expect(page.getByTestId(ui.confirmation)).toHaveCount(0);
  });

  test("AC-002-05 only active options available to students are offered", async ({
    page,
  }) => {
    await openForm(page, "STUDENT");

    await expect(page.getByTestId(ui.option("ws-testing-ai"))).toBeVisible();
    await expect(
      page.getByTestId(ui.option("ws-industry-masterclass")),
    ).toHaveCount(0);
    await expect(page.getByTestId(ui.option("ws-retired"))).toHaveCount(0);
  });
});
