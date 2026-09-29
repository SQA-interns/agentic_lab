import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import fs from 'node:fs';
const dir = process.argv[2];
const api = await SwaggerParser.validate(`${dir}/openapi.yaml`);
console.log(`openapi.yaml: valid OpenAPI ${api.openapi}, ${Object.keys(api.paths).length} paths`);
const ajv = new Ajv2020({ strict: true, allErrors: true, strictRequired: false }); addFormats(ajv);
const load = (f) => JSON.parse(fs.readFileSync(`${dir}/${f}`, 'utf8'));
const copy = ajv.compile(load('registration-copy.schema.json'));
const opts = ajv.compile(load('options-config.schema.json'));
console.log('registration-copy.schema.json: compiles');
console.log('options-config.schema.json: compiles');
const ex = load('options-config.example.json');
console.log('options-config.example.json valid:', opts(ex), opts.errors ?? '');
const sample = { schemaVersion: 1, reference: '0b8f7a4e-2f53-4f0a-9d1e-6f1c2b3a4d5e', type: 'STUDENT', submittedAt: '2026-09-30T10:00:00Z',
  participant: { firstName: 'Žiga', lastName: 'Čeč', email: 'ziga@example.si', studyInstitution: 'UL FRI', studyProgramme: 'Računalništvo', studentId: '63200001' },
  options: [{ id: 'meal-lunch-day1', name: 'Lunch, day 1', category: 'MEAL' }], consents: [{ id: 'data-processing', text: 'x', givenAt: '2026-09-30T10:00:00Z' }] };
console.log('sample student copy valid:', copy(sample), copy.errors ?? '');
const bad = { ...sample, participant: { ...sample.participant, organization: 'X' } };
console.log('student copy with organization rejected:', !copy(bad));
const noMandatory = { ...ex, consents: [{ id: 'x', text: 'y', mandatory: false }] };
console.log('config without mandatory consent rejected:', !opts(noMandatory));
