/**
 * Request builders shared by the API, acceptance and security tests.
 */
import request from 'supertest';
import type { Express } from 'express';

export interface RegistrationBody {
  [key: string]: unknown;
}

export async function obtainFormToken(
  app: Express,
  variant: 'external' | 'student',
): Promise<string> {
  const response = await request(app).get(`/api/registration-config?variant=${variant}`);
  return (response.body as { formToken: string }).formToken;
}

export function validExternalBody(formToken: string, overrides: RegistrationBody = {}): RegistrationBody {
  return {
    variant: 'external',
    firstName: 'Ana',
    lastName: 'Novak',
    email: 'ana.novak@example.org',
    organization: 'Univerza v Mariboru',
    selectedOptionIds: ['workshop-ai'],
    consents: { privacy: true },
    formToken,
    website: '',
    ...overrides,
  };
}

export function validStudentBody(formToken: string, overrides: RegistrationBody = {}): RegistrationBody {
  return {
    variant: 'student',
    firstName: 'Žan',
    lastName: 'Šuštaršič',
    email: 'zan.sustarsic@student.example.org',
    studyInstitution: 'Univerza v Mariboru',
    studyProgramme: 'Računalništvo in informacijske tehnologije',
    studentId: 'F1234567',
    selectedOptionIds: ['workshop-ai', 'meal-lunch'],
    consents: { privacy: true },
    formToken,
    website: '',
    ...overrides,
  };
}

/** Obtain a fresh token and submit a registration in one step. */
export async function registerExternal(
  app: Express,
  overrides: RegistrationBody = {},
): Promise<request.Response> {
  const token = await obtainFormToken(app, 'external');
  return request(app)
    .post('/api/registrations')
    .set('Content-Type', 'application/json')
    .send(validExternalBody(token, overrides));
}

export async function registerStudent(
  app: Express,
  overrides: RegistrationBody = {},
): Promise<request.Response> {
  const token = await obtainFormToken(app, 'student');
  return request(app)
    .post('/api/registrations')
    .set('Content-Type', 'application/json')
    .send(validStudentBody(token, overrides));
}

export const EXPORT_AUTH = `Basic ${Buffer.from('organizer:organizer-password').toString('base64')}`;
