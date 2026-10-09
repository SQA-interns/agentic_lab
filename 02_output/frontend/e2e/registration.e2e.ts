import { expect, test } from "@playwright/test";
import {
  UUID_PATTERN,
  awaitMessage,
  basicAuth,
  fillRegistration,
  mailpitUrl,
  organizer,
  uniqueEmail,
} from "./support/stack";

test.describe("Registration through the running stack", () => {
  test("AC-001-01 AC-004-01 AC-006-01 NFR-01 an external participant with Slovenian characters registers", async ({
    page,
    request,
  }) => {
    const email = uniqueEmail("crtomir.e2e");
    await page.goto("/");
    await fillRegistration(page, "EXTERNAL", {
      firstName: "Črtomir",
      lastName: "Šuštar Žižek",
      email,
      organization: "Univerza v Ljubljani – FRI",
    });
    await page.getByTestId("option-ws-ai").check();

    await page.getByTestId("submit").click();

    const confirmation = page.getByTestId("confirmation");
    await expect(confirmation).toContainText(
      "Thank you, Črtomir Šuštar Žižek. Your registration has been received.",
    );
    const id = (await confirmation.textContent())?.match(UUID_PATTERN)?.[0];
    expect(id).toBeTruthy();
    const message = await awaitMessage(request, email, "Črtomir Šuštar Žižek");
    expect(message.Subject).toContain("Registration confirmation");
    expect(message.Text).toContain(`Registration ID: ${id}`);
  });

  test("AC-002-01 AC-004-01 a student registers", async ({ page }) => {
    await page.goto("/");
    await fillRegistration(page, "STUDENT", {
      firstName: "Žana",
      lastName: "Kovač",
      email: uniqueEmail("zana.e2e"),
      studyInstitution: "Fakulteta za računalništvo in informatiko",
      studyProgramme: "Računalništvo in informatika",
      studentId: "63209999",
    });
    await page.getByTestId("option-other-career-fair").check();

    await page.getByTestId("submit").click();

    await expect(page.getByTestId("confirmation")).toContainText(
      "Thank you, Žana Kovač. Your registration has been received.",
    );
  });

  test("AC-001-17 the page shows an invalid email next to the field and sends nothing", async ({
    page,
  }) => {
    let posts = 0;
    page.on("request", (request) => {
      if (request.method() === "POST" && request.url().endsWith("/api/registrations")) {
        posts++;
      }
    });
    await page.goto("/");
    await fillRegistration(page, "EXTERNAL", {
      firstName: "Ana",
      lastName: "Novak",
      email: "ana@example",
      organization: "IJS",
    });

    await page.getByTestId("submit").click();

    await expect(page.getByTestId("error-email")).toBeVisible();
    await expect(page.getByTestId("confirmation")).toHaveCount(0);
    expect(posts).toBe(0);
  });

  test("AC-003-03 each form offers only the options available to its type", async ({ page }) => {
    await page.goto("/");

    await page.getByTestId("type-student").check();
    await expect(page.getByTestId("option-other-career-fair")).toBeVisible();
    await expect(page.getByTestId("option-ev-industry-dinner")).toHaveCount(0);

    await page.getByTestId("type-external").check();
    await expect(page.getByTestId("option-ev-industry-dinner")).toBeVisible();
    await expect(page.getByTestId("option-other-career-fair")).toHaveCount(0);
  });

  test("AC-007-02 the organizer email carries the registration JSON", async ({ page, request }) => {
    const email = uniqueEmail("organizer.copy.e2e");
    await page.goto("/");
    await fillRegistration(page, "EXTERNAL", {
      firstName: "Maja",
      lastName: "Zupan",
      email,
      organization: "Zavod",
    });
    await page.getByTestId("submit").click();
    const confirmation = page.getByTestId("confirmation");
    await expect(confirmation).toBeVisible();
    const id = (await confirmation.textContent())?.match(UUID_PATTERN)?.[0];
    expect(id).toBeTruthy();

    const message = await awaitMessage(request, organizer().emails[0]!, `Registration ID: ${id}`);

    expect(message.Attachments.map((a) => a.FileName)).toEqual([`registration-${id}.json`]);
    const attachment = await request.get(
      `${mailpitUrl}/api/v1/message/${message.ID}/part/${message.Attachments[0]!.PartID}`,
    );
    const copy = await attachment.json();
    expect(copy.registrationId).toBe(id);
    expect(copy.participant.email).toBe(email);
  });

  test("AC-008-01 AC-008-02 only the organizer can download the workbook", async ({ request }) => {
    const anonymous = await request.get("/api/export");
    expect(anonymous.status()).toBe(401);

    const wrong = await request.get("/api/export", {
      headers: { Authorization: basicAuth(organizer().username, "not-the-password") },
    });
    expect(wrong.status()).toBe(401);

    const { username, password } = organizer();
    const response = await request.get("/api/export", {
      headers: { Authorization: basicAuth(username, password) },
    });
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain(
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    );
    const body = await response.body();
    expect(body.subarray(0, 2).toString("latin1")).toBe("PK");
  });
});
