import { inflateRawSync } from 'node:zlib';

// Minimal reader for the export workbook: unzips the package and returns the first sheet's
// rows as cell texts (shared or inline strings), enough to check exported values.

function unzip(data: Buffer): Map<string, Buffer> {
  let eocd = data.length - 22;
  while (eocd >= 0 && data.readUInt32LE(eocd) !== 0x06054b50) {
    eocd -= 1;
  }
  if (eocd < 0) {
    throw new Error('not a zip file');
  }
  const entries = data.readUInt16LE(eocd + 10);
  let offset = data.readUInt32LE(eocd + 16);
  const files = new Map<string, Buffer>();
  for (let i = 0; i < entries; i += 1) {
    if (data.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error('bad central directory');
    }
    const method = data.readUInt16LE(offset + 10);
    const compressedSize = data.readUInt32LE(offset + 20);
    const nameLength = data.readUInt16LE(offset + 28);
    const extraLength = data.readUInt16LE(offset + 30);
    const commentLength = data.readUInt16LE(offset + 32);
    const localHeader = data.readUInt32LE(offset + 42);
    const name = data.toString('utf8', offset + 46, offset + 46 + nameLength);
    const localNameLength = data.readUInt16LE(localHeader + 26);
    const localExtraLength = data.readUInt16LE(localHeader + 28);
    const start = localHeader + 30 + localNameLength + localExtraLength;
    const raw = data.subarray(start, start + compressedSize);
    files.set(name, method === 8 ? inflateRawSync(raw) : Buffer.from(raw));
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

function decode(xml: string): string {
  return xml
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec: string) => String.fromCodePoint(parseInt(dec, 10)))
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function texts(fragment: string): string {
  return [...fragment.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)]
    .map((m) => decode(m[1]))
    .join('');
}

function columnIndex(ref: string): number {
  const letters = ref.replace(/\d+/g, '');
  let index = 0;
  for (const ch of letters) {
    index = index * 26 + (ch.charCodeAt(0) - 64);
  }
  return index - 1;
}

export interface Sheet {
  name: string;
  rows: string[][];
}

export function readFirstSheet(xlsx: Buffer): Sheet {
  const files = unzip(xlsx);
  const workbook = files.get('xl/workbook.xml')?.toString('utf8') ?? '';
  const name = decode(/<sheet\s[^>]*name="([^"]*)"/.exec(workbook)?.[1] ?? '');
  const shared = files.get('xl/sharedStrings.xml')?.toString('utf8') ?? '';
  const strings = [...shared.matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) => texts(m[1]));
  const sheetXml = files.get('xl/worksheets/sheet1.xml')?.toString('utf8');
  if (!sheetXml) {
    throw new Error('no first worksheet');
  }
  const rows: string[][] = [];
  for (const row of sheetXml.matchAll(/<row[^>]*?(?:\/>|>([\s\S]*?)<\/row>)/g)) {
    const cells: string[] = [];
    for (const cell of (row[1] ?? '').matchAll(/<c\s([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attributes = cell[1];
      const body = cell[2] ?? '';
      const ref = /r="([A-Z]+\d+)"/.exec(attributes)?.[1] ?? 'A1';
      const type = /t="([^"]+)"/.exec(attributes)?.[1];
      const value = /<v>([\s\S]*?)<\/v>/.exec(body)?.[1];
      let text = '';
      if (type === 's' && value !== undefined) {
        text = strings[Number(value)] ?? '';
      } else if (type === 'inlineStr') {
        text = texts(body);
      } else if (value !== undefined) {
        text = decode(value);
      }
      cells[columnIndex(ref)] = text;
    }
    rows.push(Array.from(cells, (c) => c ?? ''));
  }
  return { name, rows };
}
