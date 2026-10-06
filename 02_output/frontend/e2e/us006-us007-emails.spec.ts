import { expect, test } from "@playwright/test";
import {
  MAILPIT_URL,
  awaitMail,
  expectConfirmation,
  organizer,
  registerExternal,
  uniqueEmail,
} from "./support";

test("AC-006-01 AC-006-02 the participant receives a confirmation email", async ({
  page,
  request,
}) => {
  const email = uniqueEmail("ziga.sustar");
  await registerExternal(
    page,
    { firstName: "Žiga", lastName: "Šuštar", email, organization: "Inštitut" },
    ["Workshop: Open data"],
  );
  await expectConfirmation(page, "Žiga", email);

  const message = await awaitMail(request, email, "Šuštar");

  expect(message.To.map((t) => t.Address)).toEqual([email]);
  expect(message.Text).toContain("Žiga Šuštar");
  expect(message.Text).toContain("External participant");
  expect(message.Text).toContain("Workshop: Open data");
});

test("AC-007-01 AC-007-04 organizers receive the registration with the JSON attached", async ({
  page,
  request,
}) => {
  const { emails } = organizer();
  const email = uniqueEmail("spela.cuk");
  await registerExternal(page, {
    firstName: "Špela",
    lastName: "Čuk",
    email,
    organization: "Šola",
  });
  await expectConfirmation(page, "Špela", email);

  const message = await awaitMail(request, emails[0]!, email);

  expect(message.To.map((t) => t.Address).sort()).toEqual([...emails].sort());
  expect(message.Text).toContain("Špela");
  expect(message.Text).toContain("Čuk");
  expect(message.Attachments).toHaveLength(1);
  expect(message.Attachments[0]!.FileName).toMatch(/^registration-[0-9a-f-]{36}\.json$/);
  const attachment = await request.get(
    `${MAILPIT_URL}/api/v1/message/${message.ID}/part/${message.Attachments[0]!.PartID}`,
  );
  const copy = JSON.parse(await attachment.text()) as {
    participant: { firstName: string; email: string };
  };
  expect(copy.participant.firstName).toBe("Špela");
  expect(copy.participant.email).toBe(email);
});
