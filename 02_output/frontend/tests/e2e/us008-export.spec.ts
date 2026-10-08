// US-008 Registration export through the frontend proxy, end to end.
import { expect, test } from "@playwright/test";
import { organizerAuth } from "./helpers";

test("AC-008-01 AC-008-02 the export is a workbook for organizers and refused otherwise", async ({
  request,
}) => {
  const refused = await request.get("/api/export");
  expect(refused.status()).toBe(401);

  const wrong = await request.get("/api/export", {
    headers: {
      Authorization: `Basic ${Buffer.from("e2e-organizer:wrong-pass").toString("base64")}`,
    },
  });
  expect(wrong.status()).toBe(401);

  const exported = await request.get("/api/export", {
    headers: { Authorization: organizerAuth() },
  });
  expect(exported.status()).toBe(200);
  expect(exported.headers()["content-type"]).toContain(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  );
  const body = await exported.body();
  expect(body.subarray(0, 2).toString("latin1")).toBe("PK");
});
