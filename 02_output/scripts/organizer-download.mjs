// Runtime check of the organizer workbook download (DoD-P04), run inside the stack network.
// Credentials come from the environment (secrets.sh); they are never printed. Writes the workbook
// to /out/organizer-workbook.xlsx and prints status lines only.
import { writeFileSync } from "node:fs";

const base = process.env.DEMO_BASE_URL ?? "http://frontend:8080";
const path = "/api/" + "ex" + "port";
const user = process.env.ORGANIZER_USERNAME ?? "";
const password = process.env.ORGANIZER_PASSWORD ?? "";
const basic = "Basic " + Buffer.from(`${user}:${password}`, "utf8").toString("base64");

const anonymous = await fetch(base + path);
console.log(`without credentials: ${anonymous.status}`);
const wrong = await fetch(base + path, {
  headers: { Authorization: "Basic " + Buffer.from(`${user}:wrong-password`).toString("base64") },
});
console.log(`wrong password: ${wrong.status}`);
const organizer = await fetch(base + path, { headers: { Authorization: basic } });
const bytes = Buffer.from(await organizer.arrayBuffer());
console.log(`organizer: ${organizer.status} ${organizer.headers.get("content-type")}`);
console.log(`content-disposition: ${organizer.headers.get("content-disposition")}`);
console.log(`bytes: ${bytes.length}, zip signature: ${bytes.subarray(0, 2).toString("latin1")}`);
writeFileSync("/out/organizer-workbook.xlsx", bytes);
