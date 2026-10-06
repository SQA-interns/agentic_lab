// Runtime demonstration of DoD-P04 (phase 6): the organizer downloads the workbook from the running
// stack; it is saved to reports/runtime-demo-export.xlsx so it can be opened and inspected.
import { expect, test } from "@playwright/test";
import { writeFileSync } from "node:fs";
import { organizerAuthorization } from "./support";

test("DoD-P04 the organizer downloads a workbook that is saved for inspection", async ({
  request,
}) => {
  const response = await request.get("/api/registrations/export", {
    headers: { Authorization: organizerAuthorization() },
  });

  expect(response.status()).toBe(200);
  const body = await response.body();
  expect(body.subarray(0, 2).toString("latin1")).toBe("PK");
  writeFileSync("reports/runtime-demo-export.xlsx", body);
});
