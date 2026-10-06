import { expect } from "@playwright/test";
import { MAILPIT_URL } from "./stack";

export type Message = {
  ID: string;
  Subject: string;
  Text: string;
  HTML: string;
  To: { Address: string }[];
  Attachments: { PartID: string; FileName: string; ContentType: string }[];
};

async function get(path: string): Promise<Response> {
  const r = await fetch(MAILPIT_URL + path);
  if (!r.ok) throw new Error(`Mailpit ${path} -> ${r.status}`);
  return r;
}

export async function messagesTo(address: string): Promise<Message[]> {
  const q = encodeURIComponent(`to:"${address}"`);
  const list = (await (await get(`/api/v1/search?query=${q}`)).json()) as {
    messages: { ID: string }[];
  };
  return Promise.all(
    list.messages.map(
      async (m) => (await (await get(`/api/v1/message/${m.ID}`)).json()) as Message,
    ),
  );
}

/** Waits for a message to the address whose text contains the marker. */
export async function awaitMessage(address: string, marker: string): Promise<Message> {
  let found: Message | undefined;
  await expect
    .poll(
      async () => {
        found = (await messagesTo(address)).find((m) => m.Text.includes(marker));
        return found !== undefined;
      },
      { timeout: 30_000 },
    )
    .toBe(true);
  return found as Message;
}

export async function attachment(messageId: string, partId: string): Promise<string> {
  return (await get(`/api/v1/message/${messageId}/part/${partId}`)).text();
}
