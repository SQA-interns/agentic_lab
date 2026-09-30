import SwaggerParser from '@apidevtools/swagger-parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { resolve } from 'node:path';
import type { OpenAPIV3 } from 'openapi-types';

// Validates live API responses against docs/02_contracts/openapi.yaml (DoD-P04).
const contractPath = resolve(import.meta.dirname, '../../../docs/02_contracts/openapi.yaml');
let apiPromise: Promise<OpenAPIV3.Document> | undefined;

function toJsonSchema(schema: unknown): unknown {
  return JSON.parse(
    JSON.stringify(schema, (_key, value: unknown) => {
      if (
        value &&
        typeof value === 'object' &&
        (value as { nullable?: boolean }).nullable === true
      ) {
        const { nullable: _omit, ...rest } = value as Record<string, unknown>;
        return { anyOf: [rest, { type: 'null' }] };
      }
      return value;
    }),
  );
}

export async function schemaErrors(schemaName: string, value: unknown): Promise<string[]> {
  apiPromise ??= SwaggerParser.dereference(contractPath) as Promise<OpenAPIV3.Document>;
  const api = await apiPromise;
  const schema = api.components?.schemas?.[schemaName];
  if (!schema) return [`schema ${schemaName} not found in contract`];
  const ajv = new Ajv2020({ strict: false, allErrors: true });
  addFormats(ajv);
  const validate = ajv.compile(toJsonSchema(schema) as object);
  return validate(value)
    ? []
    : (validate.errors ?? []).map((e) => `${e.instancePath} ${e.message ?? ''}`);
}
