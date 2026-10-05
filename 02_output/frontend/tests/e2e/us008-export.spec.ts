// US-008 end to end: the organizer export through the same origin as the page.
import { expect, test } from "@playwright/test";
import { organizerAuthorization } from "./support";

const XLSX =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

test("AC-008-01 an organizer downloads the registrations as an Excel workbook", async ({
  request,
}) => {
  const response = await request.get("/api/registrations/export", {
    headers: { Authorization: organizerAuthorization() },
  });

  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain(XLSX);
  expect(response.headers()["content-disposition"]).toContain(
    "registrations.xlsx",
  );
  const body = await response.body();
  expect(body.subarray(0, 2).toString("latin1")).toBe("PK");
});

test("AC-008-02 the export without organizer access is refused", async ({
  request,
}) => {
  const response = await request.get("/api/registrations/export");

  expect(response.status()).toBe(401);
  expect(response.headers()["content-type"] ?? "").not.toContain(XLSX);
});

test("AC-008-03 the export with wrong organizer credentials is refused", async ({
  request,
}) => {
  const response = await request.get("/api/registrations/export", {
    headers: {
      Authorization:
        "Basic " +
        Buffer.from("organizer:wrong-password", "utf8").toString("base64"),
    },
  });

  expect(response.status()).toBe(401);
  expect(response.headers()["content-type"] ?? "").not.toContain(XLSX);
});
