// Validates the JSON contracts in docs/02_contracts: every schema compiles (JSON Schema
// 2020-12, strict mode) and every example document validates against its schema.
// Uses the ajv version locked in frontend/package-lock.json (D-14).
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(join(root, "frontend", "package.json"));
const Ajv2020 = require("ajv/dist/2020");

const contracts = join(root, "docs", "02_contracts");
const load = (name) => JSON.parse(readFileSync(join(contracts, name), "utf8"));

// schema file -> documents that must validate against it
const cases = {
  "registration-copy.schema.json": [
    "examples/registration-copy.external.json",
    "examples/registration-copy.student.json",
  ],
  "conference-options.schema.json": ["examples/conference-options.json"],
  "email-messages.schema.json": [
    "examples/email.participant-confirmation.json",
    "examples/email.organizer-notification.json",
  ],
  "recaptcha-verify.schema.json": ["examples/recaptcha-verify.json"],
  "ui-form.schema.json": ["registration-form.ui.json"],
};

const ajv = new Ajv2020({
  strict: true,
  strictRequired: false,
  allErrors: true,
  validateFormats: false,
});
let failures = 0;
let documents = 0;

for (const [schemaFile, docs] of Object.entries(cases)) {
  let validate;
  try {
    validate = ajv.compile(load(schemaFile));
    console.log(`schema ok      ${schemaFile}`);
  } catch (error) {
    failures += 1;
    console.log(`schema INVALID ${schemaFile}: ${error.message}`);
    continue;
  }
  for (const doc of docs) {
    documents += 1;
    if (validate(load(doc))) {
      console.log(`document ok    ${doc}`);
    } else {
      failures += 1;
      console.log(`document INVALID ${doc}: ${ajv.errorsText(validate.errors)}`);
    }
  }
}

console.log(`json contracts: schemas=${Object.keys(cases).length} documents=${documents} failures=${failures}`);
process.exit(failures === 0 ? 0 : 1);
