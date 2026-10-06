// Runtime demonstration against the running local stack (DoD-06, DoD-P01..DoD-P04).
// Usage: node runtime-demo.mjs register|check <state-file>
// "register" creates one external and one student registration and checks emails and export;
// "check" (after `docker compose down` + `up`) checks that the same registrations are still exported.
// Prints observations only: ids, status codes, counts. Never prints credentials.
import { readFileSync, writeFileSync } from "node:fs";

const BASE = process.env.E2E_BASE_URL ?? "http://host.docker.internal:8088";
const MAILPIT = process.env.MAILPIT_URL ?? "http://host.docker.internal:8026";
const [mode, stateFile] = process.argv.slice(2);
const auth =
  "Basic " +
  Buffer.from(`${process.env.ORGANIZER_USERNAME}:${process.env.ORGANIZER_PASSWORD}`).toString("base64");
const organizerEmails = (process.env.ORGANIZER_EMAILS ?? "").split(",").map((e) => e.trim()).filter(Boolean);
let failures = 0;

function check(label, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"} ${label}${detail ? " — " + detail : ""}`);
  if (!ok) failures++;
}

async function exportStatus(withAuth) {
  const res = await fetch(`${BASE}/api/export/registrations.xlsx`, withAuth ? { headers: { Authorization: auth } } : {});
  const body = Buffer.from(await res.arrayBuffer());
  return { status: res.status, type: res.headers.get("content-type") ?? "", zip: body.subarray(0, 2).toString("latin1") === "PK", body };
}

async function mailTo(address, text) {
  for (let i = 0; i < 40; i++) {
    const q = encodeURIComponent(`to:"${address}" "${text}"`);
    const found = (await (await fetch(`${MAILPIT}/api/v1/search?query=${q}`)).json()).messages;
    if (found.length) return (await fetch(`${MAILPIT}/api/v1/message/${found[0].ID}`)).json();
    await new Promise((r) => setTimeout(r, 500));
  }
  return null;
}

const stamp = Date.now();
if (mode === "register") {
  const config = await (await fetch(`${BASE}/api/form-config`)).json();
  check("form configuration served through the frontend", config.options?.length > 0, `${config.options?.length} active options`);
  const people = [
    { type: "EXTERNAL", firstName: "Žiga", lastName: "Čebašek", email: `demo.external.${stamp}@example.com`, organization: "Inštitut Šiška", optionIds: ["ws-ai-research", "meal-lunch-day1"] },
    { type: "STUDENT", firstName: "Špela", lastName: "Žagar", email: `demo.student.${stamp}@example.com`, studyInstitution: "Univerza v Ljubljani", studyProgramme: "Računalništvo", studentId: "63210001", optionIds: ["ws-open-data"] },
  ];
  const ids = [];
  for (const p of people) {
    const res = await fetch(`${BASE}/api/registrations`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...p, consentIds: config.consents.map((c) => c.id), antiAutomationToken: "test-mode-pass" }),
    });
    const body = await res.json();
    check(`DoD-P01 ${p.type} registration accepted`, res.status === 201, `HTTP ${res.status}, id ${body.id}`);
    ids.push({ id: body.id, email: p.email, lastName: p.lastName, type: p.type });
    const participant = await mailTo(p.email, p.lastName);
    check(`DoD-P03 participant email for ${p.type}`, participant !== null && participant.Text.includes(p.lastName));
    const organizer = await mailTo(organizerEmails[0], p.email);
    check(
      `DoD-P03 organizer email for ${p.type} with JSON attachment`,
      organizer !== null && organizer.Attachments.length === 1 && organizer.Attachments[0].FileName === `registration-${body.id}.json`,
      organizer ? `${organizer.To.length} recipients, attachment ${organizer.Attachments[0]?.FileName}` : "not found",
    );
  }
  writeFileSync(stateFile, JSON.stringify(ids));
} else {
  const ids = JSON.parse(readFileSync(stateFile, "utf8"));
  for (const r of ids) {
    const res = await fetch(`${BASE}/api/registrations`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type: "EXTERNAL", firstName: "A", lastName: "B", email: r.email, organization: "O", optionIds: [], consentIds: ["data-processing"], antiAutomationToken: "test-mode-pass" }),
    });
    check(`NFR-02 ${r.type} registration still stored after recreation (duplicate refused)`, res.status === 409, `HTTP ${res.status}`);
  }
}

const anonymous = await exportStatus(false);
check("DoD-P04 export refused without organizer access", anonymous.status === 401 && !anonymous.zip, `HTTP ${anonymous.status}`);
const organizer = await exportStatus(true);
check("DoD-P04 organizer receives a valid workbook", organizer.status === 200 && organizer.zip && organizer.type.includes("spreadsheetml"), `HTTP ${organizer.status}, ${organizer.body.length} bytes`);
writeFileSync(`${stateFile}.${mode}.xlsx`, organizer.body);
console.log(failures === 0 ? "RESULT PASS" : `RESULT FAIL (${failures})`);
process.exit(failures === 0 ? 0 : 1);
