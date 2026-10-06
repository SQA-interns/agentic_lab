import { expect, test } from "@playwright/test";
import { expectConfirmation, organizer, registerExternal, uniqueEmail } from "./support";

const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

test("AC-008-01 an organizer downloads the registrations workbook", async ({ page, request }) => {
  const { username, password } = organizer();
  const email = uniqueEmail("export.check");
  await registerExternal(page, {
    firstName: "Export",
    lastName: "Check",
    email,
    organization: "Org",
  });
  await expectConfirmation(page, "Export", email);

  const response = await request.get("/api/export/registrations.xlsx", {
    headers: {
      Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`,
    },
  });

  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain(XLSX);
  expect(response.headers()["content-disposition"]).toContain("attachment");
  const body = await response.body();
  expect(body.subarray(0, 2).toString("latin1")).toBe("PK");
});

test("AC-008-02 the export is refused without organizer access", async ({ request }) => {
  const response = await request.get("/api/export/registrations.xlsx");

  expect(response.status()).toBe(401);
  expect(response.headers()["content-type"] ?? "").not.toContain(XLSX);
});
