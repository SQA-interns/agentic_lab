import { E2E_MAILPIT_PORT } from "./stack";

const BASE = `http://127.0.0.1:${E2E_MAILPIT_PORT}/api/v1`;

export interface MailMessage {
  ID: string;
  Subject: string;
  To: { Address: string }[];
  Text: string;
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
}

/** Full messages containing the term, addressed to the recipient. */
export async function messagesTo(recipient: string, term: string): Promise<MailMessage[]> {
  const search = await fetch(`${BASE}/search?query=${encodeURIComponent(`"${term}"`)}`);
  const result = (await search.json()) as { messages: { ID: string }[] };
  const messages: MailMessage[] = [];
  for (const summary of result.messages ?? []) {
    const message = (await (await fetch(`${BASE}/message/${summary.ID}`)).json()) as MailMessage;
    if (message.To.some((to) => to.Address === recipient)) {
      messages.push(message);
    }
  }
  return messages;
}

export async function attachmentText(messageId: string, partId: string): Promise<string> {
  return (await fetch(`${BASE}/message/${messageId}/part/${partId}`)).text();
}
