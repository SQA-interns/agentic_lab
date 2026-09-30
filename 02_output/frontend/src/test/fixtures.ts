import type { Catalog } from '../api';

export const catalog: Catalog = {
  conferenceTitle: 'Lab Conference',
  captcha: { mode: 'stub', siteKey: null },
  consent: { id: 'c1', text: 'I agree to the synthetic statement.', required: true },
  groups: [
    { id: 'workshops', label: 'Workshops', options: [{ id: 'ws-a', name: 'Workshop A' }] },
    { id: 'events', label: 'Events', options: [] },
    { id: 'meals', label: 'Meals', options: [{ id: 'meal-a', name: 'Lunch' }] },
    { id: 'other', label: 'Other activities', options: [] },
  ],
};

export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
