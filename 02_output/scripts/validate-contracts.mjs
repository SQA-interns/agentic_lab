// Validates the JSON contracts in docs/02_contracts with ajv (JSON Schema 2020-12):
// every *.schema.json compiles, its "examples" validate against it, and every instance file
// listed below validates against its schema. Prints one line per check; exit 1 on any failure.
//   node validate-contracts.mjs
import { readFileSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const contracts = join(here, '..', 'docs', '02_contracts');
const require = createRequire(join(here, '..', 'frontend', 'package.json'));
const Ajv2020 = require('ajv/dist/2020').default;

// Instance files and the schema each must satisfy.
const instances = {
  'conference-options.example.json': 'conference-options.schema.json',
  'registration-form.ui.json': 'ui-contract.schema.json',
};

// strictRequired is off: "not": { "required": [...] } is the intended way to forbid fields of
// the other registration type. Formats are not validated (ajv-formats is not in the tech stack).
const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  strictRequired: false,
  validateFormats: false,
});
const read = (name) => JSON.parse(readFileSync(join(contracts, name), 'utf8'));
let failed = 0;

function check(label, validate, data) {
  if (validate(data)) {
    console.log(`ok    ${label}`);
  } else {
    failed += 1;
    console.log(`FAIL  ${label}: ${ajv.errorsText(validate.errors)}`);
  }
}

const schemaFiles = readdirSync(contracts).filter((f) => f.endsWith('.schema.json')).sort();
const compiled = {};
for (const file of schemaFiles) {
  try {
    const schema = read(file);
    compiled[file] = ajv.compile(schema);
    console.log(`ok    ${file} compiles`);
    (schema.examples ?? []).forEach((example, i) =>
      check(`${file} example ${i + 1}`, compiled[file], example),
    );
  } catch (e) {
    failed += 1;
    console.log(`FAIL  ${file}: ${e.message}`);
  }
}

for (const [file, schemaFile] of Object.entries(instances)) {
  if (!compiled[schemaFile]) {
    failed += 1;
    console.log(`FAIL  ${file}: schema ${schemaFile} not compiled`);
    continue;
  }
  check(`${file} against ${schemaFile}`, compiled[schemaFile], read(file));
}

console.log(`${failed === 0 ? 'all valid' : `${failed} failed`}`);
process.exit(failed === 0 ? 0 : 1);
