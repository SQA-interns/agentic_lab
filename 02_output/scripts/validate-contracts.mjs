// Compiles every JSON Schema in docs/02_contracts with Ajv (JSON Schema 2020-12, strict mode)
// and validates every instance next to it (<name>.example.json and <name>.json against
// <name>.schema.json). Formats are annotations only (no ajv-formats in project/stack.md).
// Usage: node validate-contracts.mjs <contracts dir>
import { createRequire } from "node:module";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, "..", "frontend", "package.json"));
const Ajv2020 = require("ajv/dist/2020").default;

const dir = resolve(process.argv[2] ?? join(here, "..", "docs", "02_contracts"));
const files = existsSync(dir) ? readdirSync(dir) : [];
const schemas = files.filter((f) => f.endsWith(".schema.json"));
let failures = 0;
let examples = 0;

for (const name of schemas) {
  const ajv = new Ajv2020({ strict: true, allErrors: true, validateFormats: false });
  try {
    const validate = ajv.compile(JSON.parse(readFileSync(join(dir, name), "utf8")));
    console.log(`schema ${name}: valid`);
    const base = name.replace(/\.schema\.json$/, "");
    for (const instance of [`${base}.example.json`, `${base}.json`]) {
      if (!files.includes(instance)) continue;
      examples++;
      const ok = validate(JSON.parse(readFileSync(join(dir, instance), "utf8")));
      console.log(`instance ${instance}: ${ok ? "valid" : "INVALID " + ajv.errorsText(validate.errors)}`);
      if (!ok) failures++;
    }
  } catch (e) {
    failures++;
    console.log(`schema ${name}: INVALID ${e.message}`);
  }
}
console.log(`SUMMARY schemas=${schemas.length} examples=${examples} failures=${failures}`);
process.exit(failures === 0 ? 0 : 1);
