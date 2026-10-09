// Compiles every JSON Schema (*.schema.json) in the given directory with Ajv (JSON Schema
// 2020-12) and validates the instances <name>.json and <name>.example.json against
// <name>.schema.json. Formats are annotations only. Prints one line per file; exits 1 when any
// schema or instance is invalid.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import Ajv2020 from "ajv/dist/2020.js";

const dir = process.argv[2];
if (!dir) {
  console.error("usage: node scripts/validate-schemas.mjs <directory>");
  process.exit(2);
}

const ajv = new Ajv2020({ strict: true, allErrors: true, validateFormats: false });
let failed = 0;
const files = readdirSync(dir);
for (const file of files.filter((f) => f.endsWith(".schema.json"))) {
  const name = file.slice(0, -".schema.json".length);
  try {
    const validate = ajv.compile(JSON.parse(readFileSync(join(dir, file), "utf8")));
    console.log(`${file}: valid schema`);
    for (const instance of [`${name}.json`, `${name}.example.json`].filter((f) =>
      files.includes(f),
    )) {
      const ok = validate(JSON.parse(readFileSync(join(dir, instance), "utf8")));
      console.log(`${instance}: ${ok ? "valid" : "INVALID " + ajv.errorsText(validate.errors)}`);
      if (!ok) failed++;
    }
  } catch (error) {
    console.log(`${file}: INVALID ${error.message}`);
    failed++;
  }
}
process.exit(failed === 0 ? 0 : 1);
