// Mechanical and semantic validation of docs/02_contracts (phase 2 gate, DoD-P04).
// Usage: node scripts/validate-contracts.mjs [contractsDir] [catalogFile...]
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const here = new URL('.', import.meta.url).pathname;
const contracts = resolve(process.argv[2] ?? join(here, '../../docs/02_contracts'));
const catalogFiles = process.argv.slice(3).length
  ? process.argv.slice(3)
  : [resolve(here, '../../config/conference.local.json')];
const failures = [];
const check = (ok, message) => {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${message}`);
  if (!ok) failures.push(message);
};
const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));

// 1. OpenAPI document is valid OpenAPI 3.0 and fully dereferences.
const api = await SwaggerParser.validate(join(contracts, 'openapi.yaml'));
check(api.openapi === '3.0.3', 'openapi.yaml is a valid OpenAPI 3.0.3 document');

// 2. Every example in the OpenAPI document validates against its schema.
const oasAjv = new Ajv2020({ strict: false, allErrors: true });
addFormats(oasAjv);
const toJsonSchema = (schema) =>
  JSON.parse(
    JSON.stringify(schema, (key, value) => {
      if (value && typeof value === 'object' && value.nullable === true) {
        const { nullable: _nullable, ...rest } = value;
        return { anyOf: [rest, { type: 'null' }] };
      }
      return value;
    }),
  );
let examples = 0;
const visitMedia = (label, media) => {
  for (const [type, content] of Object.entries(media ?? {})) {
    if (content.example !== undefined && content.schema) {
      examples += 1;
      const ok = oasAjv.validate(toJsonSchema(content.schema), content.example);
      check(ok, `${label} ${type} example matches schema ${ok ? '' : oasAjv.errorsText()}`);
    }
  }
};
for (const [path, item] of Object.entries(api.paths)) {
  for (const [method, op] of Object.entries(item)) {
    visitMedia(`${method.toUpperCase()} ${path} request`, op.requestBody?.content);
    for (const [status, response] of Object.entries(op.responses ?? {})) {
      visitMedia(`${method.toUpperCase()} ${path} ${status}`, response.content);
    }
  }
}
check(examples >= 4, `OpenAPI contains request/response examples (${examples})`);

// 3. JSON Schemas compile (draft 2020-12) and their examples validate.
const ajv = new Ajv2020({
  strict: true,
  strictRequired: false,
  allErrors: true,
  allowUnionTypes: true,
});
addFormats(ajv);
const recordSchema = readJson(join(contracts, 'registration-record.schema.json'));
const catalogSchema = readJson(join(contracts, 'catalog-config.schema.json'));
const validateRecord = ajv.compile(recordSchema);
const validateCatalog = ajv.compile(catalogSchema);
check(true, 'registration-record.schema.json and catalog-config.schema.json compile');
for (const file of readdirSync(join(contracts, 'examples')).filter((f) =>
  f.startsWith('registration-record'),
)) {
  const ok = validateRecord(readJson(join(contracts, 'examples', file)));
  check(
    ok,
    `examples/${file} matches registration-record schema ${ok ? '' : ajv.errorsText(validateRecord.errors)}`,
  );
}
const badRecord = readJson(join(contracts, 'examples', 'registration-record.student.json'));
badRecord.participant.organization = 'mixed';
check(
  !validateRecord(badRecord),
  'registration-record schema rejects a student record with organization',
);

// 4. Catalog files validate and have unique option IDs across groups.
for (const file of catalogFiles) {
  const catalog = readJson(file);
  const ok = validateCatalog(catalog);
  check(
    ok,
    `${file} matches catalog-config schema ${ok ? '' : ajv.errorsText(validateCatalog.errors)}`,
  );
  const ids = Object.values(catalog.groups ?? {})
    .flat()
    .map((o) => o.id);
  check(new Set(ids).size === ids.length, `${file} option IDs are unique across groups`);
}

// 5. Cross-contract consistency.
const requestProps = (name) => {
  const s = api.components.schemas[name];
  return new Set(s.allOf.flatMap((part) => Object.keys(part.properties ?? {})));
};
const participantFields = Object.keys(recordSchema.properties.participant.properties);
const requestFields = new Set([
  ...requestProps('ExternalRegistrationRequest'),
  ...requestProps('StudentRegistrationRequest'),
]);
check(
  participantFields.every((f) => requestFields.has(f)),
  'every stored participant field is a request field',
);
const workbook = readJson(join(contracts, 'export-workbook.json'));
const recordPaths = new Set([
  ...Object.keys(recordSchema.properties),
  ...participantFields.map((f) => `participant.${f}`),
  ...Object.keys(recordSchema.properties.selections.properties).map((g) => `selections.${g}`),
  'consent.given',
]);
check(
  workbook.columns.every((c) => recordPaths.has(c.source)),
  'every export column maps to a registration-record field',
);
const groupEnum = api.components.schemas.GroupId.enum;
check(
  JSON.stringify(groupEnum) ===
    JSON.stringify(Object.keys(catalogSchema.properties.groups.properties)),
  'catalog groups equal API group IDs (workshops, events, meals, other)',
);
const selectionGroups = Object.keys(api.components.schemas.Selections.properties);
check(
  JSON.stringify(selectionGroups) === JSON.stringify(groupEnum),
  'request selection groups equal API group IDs',
);

console.log(
  failures.length
    ? `\n${failures.length} contract check(s) failed`
    : '\nAll contract checks passed',
);
process.exit(failures.length ? 1 : 0);
