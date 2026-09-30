import { expect, type APIRequestContext } from '@playwright/test';
import { mailpitUrl } from './env';

export interface MailSummary {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

export interface MailMessage extends MailSummary {
  Text: string;
  HTML: string;
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
}

export async function messagesTo(
  request: APIRequestContext,
  address: string,
): Promise<MailSummary[]> {
  const query = encodeURIComponent(`to:"${address}"`);
  const response = await request.get(`${mailpitUrl}/api/v1/search?limit=100&query=${query}`);
  expect(response.ok()).toBeTruthy();
  const body = (await response.json()) as { messages: MailSummary[] | null };
  return body.messages ?? [];
}

export async function awaitMessage(
  request: APIRequestContext,
  address: string,
  subjectContains = '',
): Promise<MailMessage> {
  let found: MailSummary | undefined;
  await expect
    .poll(
      async () => {
        found = (await messagesTo(request, address)).find((m) =>
          m.Subject.includes(subjectContains),
        );
        return found !== undefined;
      },
      { timeout: 60_000, intervals: [500] },
    )
    .toBe(true);
  const response = await request.get(`${mailpitUrl}/api/v1/message/${found?.ID ?? ''}`);
  return (await response.json()) as MailMessage;
}

export async function attachment(
  request: APIRequestContext,
  messageId: string,
  partId: string,
): Promise<Buffer> {
  const response = await request.get(`${mailpitUrl}/api/v1/message/${messageId}/part/${partId}`);
  expect(response.ok()).toBeTruthy();
  return response.body();
}
