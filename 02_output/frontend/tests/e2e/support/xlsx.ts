import { inflateRawSync } from "node:zlib";

/** Text of every XML part under xl/ of an .xlsx (zip) file, concatenated. */
export function xlsxText(data: Buffer): string {
  let eocd = data.length - 22;
  while (eocd >= 0 && data.readUInt32LE(eocd) !== 0x06054b50) eocd--;
  if (eocd < 0) throw new Error("not a zip file");
  const entries = data.readUInt16LE(eocd + 10);
  let p = data.readUInt32LE(eocd + 16);
  let text = "";
  for (let i = 0; i < entries; i++) {
    if (data.readUInt32LE(p) !== 0x02014b50) throw new Error("bad central directory");
    const method = data.readUInt16LE(p + 10);
    const size = data.readUInt32LE(p + 20);
    const nameLen = data.readUInt16LE(p + 28);
    const extraLen = data.readUInt16LE(p + 30);
    const commentLen = data.readUInt16LE(p + 32);
    const local = data.readUInt32LE(p + 42);
    const name = data.toString("utf8", p + 46, p + 46 + nameLen);
    p += 46 + nameLen + extraLen + commentLen;
    if (!name.startsWith("xl/") || !name.endsWith(".xml")) continue;
    const start = local + 30 + data.readUInt16LE(local + 26) + data.readUInt16LE(local + 28);
    const raw = data.subarray(start, start + size);
    text += (method === 8 ? inflateRawSync(raw) : raw).toString("utf8");
  }
  return text;
}
