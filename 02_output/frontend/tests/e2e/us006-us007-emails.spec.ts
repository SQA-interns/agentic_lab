// US-006 / US-007 end to end: the emails of a registration made through the page arrive in Mailpit.
import { expect, test } from "@playwright/test";
import {
  awaitMail,
  confirmedRegistrationId,
  formConfig,
  giveMandatoryConsentsAndCaptcha,
  mailAttachment,
  openForm,
  optionsFor,
  uniqueEmail,
} from "./support";

test("AC-006-01 AC-006-04 AC-007-01 AC-007-02 participant and organizer emails arrive with the JSON copy", async ({
  page,
  request,
}) => {
  const config = await formConfig(request);
  const option = optionsFor(config, "external")[0];
  const email = uniqueEmail("e2e.mail");
  await openForm(page);
  await page.getByLabel("First name", { exact: true }).fill("Špela");
  await page.getByLabel("Last name", { exact: true }).fill("Žnidaršič");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page
    .getByLabel("Organization / institution", { exact: true })
    .fill("Občina Črnomelj");
  await page.getByRole("checkbox", { name: option.name, exact: true }).check();
  await giveMandatoryConsentsAndCaptcha(page, config);
  await page.getByRole("button", { name: "Register" }).click();
  const registrationId = await confirmedRegistrationId(page);

  const participantMail = await awaitMail(request, (m) =>
    m.To.some((to) => to.Address.toLowerCase() === email.toLowerCase()),
  );
  expect(participantMail.Subject).toContain(config.conferenceName);
  expect(participantMail.Text).toContain("Špela Žnidaršič");
  expect(participantMail.Text).toContain(option.name);

  const organizerMail = await awaitMail(request, (m) =>
    m.Attachments.some(
      (a) => a.FileName === `registration-${registrationId}.json`,
    ),
  );
  expect(
    organizerMail.To.some(
      (to) => to.Address.toLowerCase() === email.toLowerCase(),
    ),
  ).toBe(false);
  expect(organizerMail.Text).toContain("Špela");
  expect(organizerMail.Text).toContain("Žnidaršič");
  expect(organizerMail.Text).toContain("Občina Črnomelj");
  const attachment = organizerMail.Attachments.find(
    (a) => a.FileName === `registration-${registrationId}.json`,
  )!;
  expect(attachment.ContentType).toContain("application/json");
  const copy = JSON.parse(
    (
      await mailAttachment(request, organizerMail.ID, attachment.PartID)
    ).toString("utf8"),
  );
  expect(copy.registrationId).toBe(registrationId);
  expect(copy.type).toBe("external");
  expect(copy.participant.firstName).toBe("Špela");
  expect(copy.participant.lastName).toBe("Žnidaršič");
  expect(copy.participant.organization).toBe("Občina Črnomelj");
  expect(copy.options.map((o: { id: string }) => o.id)).toEqual([option.id]);
});
