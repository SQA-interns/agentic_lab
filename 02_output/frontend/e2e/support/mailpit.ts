import { request, type APIRequestContext } from '@playwright/test';

// Reads the mail catcher of the local stack through its HTTP API.
export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://mailpit:8025';

export interface MailAddress {
  Address: string;
}

export interface MailAttachment {
  PartID: string;
  FileName: string;
  ContentType: string;
}

export interface MailMessage {
  ID: string;
  From: MailAddress;
  To: MailAddress[];
  Subject: string;
  Text: string;
  Attachments: MailAttachment[];
}

export async function mailpit(): Promise<APIRequestContext> {
  return request.newContext({ baseURL: MAILPIT_URL });
}

export async function messagesTo(api: APIRequestContext, address: string): Promise<MailMessage[]> {
  const query = encodeURIComponent(`to:"${address}"`);
  const list = await api.get(`/api/v1/search?limit=200&query=${query}`);
  if (!list.ok()) {
    throw new Error(`mailpit search failed: ${list.status()}`);
  }
  const summaries = ((await list.json()) as { messages: { ID: string }[] }).messages;
  const messages: MailMessage[] = [];
  for (const summary of summaries) {
    const detail = await api.get(`/api/v1/message/${summary.ID}`);
    messages.push((await detail.json()) as MailMessage);
  }
  return messages;
}

/** Waits for a message to the address whose text contains the marker (or any message). */
export async function awaitMessage(
  api: APIRequestContext,
  address: string,
  marker = '',
  timeoutMs = 15000,
): Promise<MailMessage> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const found = (await messagesTo(api, address)).find((m) => m.Text.includes(marker));
    if (found) {
      return found;
    }
    if (Date.now() > deadline) {
      throw new Error(`no message to ${address} containing "${marker}"`);
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
}

export async function attachment(
  api: APIRequestContext,
  messageId: string,
  partId: string,
): Promise<Buffer> {
  const response = await api.get(`/api/v1/message/${messageId}/part/${partId}`);
  if (!response.ok()) {
    throw new Error(`attachment download failed: ${response.status()}`);
  }
  return response.body();
}
