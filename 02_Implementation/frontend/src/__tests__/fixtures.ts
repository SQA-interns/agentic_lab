import type { FormConfig } from '../types';

export const CONFIG: FormConfig = {
  conferenceName: 'Test Conference',
  optionGroups: {
    workshops: [
      { id: 'ws-a', name: 'Workshop A' },
      { id: 'ws-b', name: 'Workshop B' },
    ],
    events: [{ id: 'ev-a', name: 'Event A' }],
    meals: [{ id: 'meal-a', name: 'Lunch' }],
    otherActivities: [],
  },
  consents: [{ id: 'synthetic-consent', text: 'Synthetic consent (fixture)', required: true }],
  captcha: { mode: 'test', siteKey: null, testToken: 'test-token' },
};

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export const ACCEPTED = {
  registrationId: '11111111-2222-4333-8444-555555555555',
  participantType: 'EXTERNAL',
  submittedAt: '2026-09-28T10:00:00Z',
  emailStatus: 'PENDING',
  replayed: false,
};
