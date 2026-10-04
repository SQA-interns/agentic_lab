#!/usr/bin/env node
// Fills `usage` of 03_statistics/run-log.json from a Claude Code session transcript, as defined
// in 03_statistics/metrics.md, section 2. Read-only unless --write is given.
//
// Usage (repository root):
//   node 01_input/00_general/tools/usage-from-transcript.mjs [--transcript <file.jsonl>]... [--write]
//
// - Transcripts: every --transcript given (repeat it when the run was resumed in a new session),
//   else `transcript` in run-log.json, else the newest *.jsonl of this project in
//   ~/.claude/projects/ (with a warning: another session of the same folder may be newer).
//   Subagent transcripts of each (<session>/subagents/) count too. Other sessions are never
//   picked up by themselves: a human may work in the same folder at the same time.
// - Window: from the start of each transcript to `end` of run-log.json (to now while `end` is
//   empty), so questions asked after the run do not count.
// - Each model call counts once: the transcript repeats a call on several lines (one per content
//   block), so calls are keyed by message id and the last usage of each id is taken.
// - Cost: tokens x the price row of the run's model in 03_statistics/usage.md (USD per million).
//   Without a price row the cost stays null.
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";

const args = process.argv.slice(2);
const RUN_LOG = "03_statistics/run-log.json";
const USAGE = "03_statistics/usage.md";

const runLog = JSON.parse(readFileSync(RUN_LOG, "utf8"));

function newestTranscript() {
  const escaped = resolve(".").replace(/[^A-Za-z0-9]/g, "-");
  const projects = join(homedir(), ".claude", "projects");
  const candidates = existsSync(projects)
    ? readdirSync(projects).filter((dir) => dir.toLowerCase() === escaped.toLowerCase())
    : [];
  const files = candidates.flatMap((dir) =>
    readdirSync(join(projects, dir))
      .filter((file) => file.endsWith(".jsonl"))
      .map((file) => join(projects, dir, file)),
  );
  files.sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
  return files[0];
}

const given = args
  .map((arg, index) => (args[index - 1] === "--transcript" ? arg : null))
  .filter(Boolean);
const recorded = runLog.transcript ? [runLog.transcript.replace(/^~/, homedir())] : [];
let transcripts = (given.length > 0 ? given : recorded).filter((file) => existsSync(file));
if (transcripts.length === 0) {
  const newest = newestTranscript();
  if (newest) {
    console.log("warning: no recorded transcript; using the newest session of this folder");
    transcripts = [newest];
  }
}
if (transcripts.length === 0) {
  console.error("No transcript found; pass --transcript <file.jsonl>.");
  process.exit(1);
}
const transcript = transcripts[0];
const files = [...transcripts];
for (const file of transcripts) {
  const subagentDir = join(dirname(file), basename(file, ".jsonl"), "subagents");
  if (existsSync(subagentDir)) {
    for (const sub of readdirSync(subagentDir)) {
      if (sub.endsWith(".jsonl")) files.push(join(subagentDir, sub));
    }
  }
}

// The run's own first calls come before `start` is written, so only `end` bounds the window.
const to = runLog.end ? Date.parse(runLog.end) : Infinity;
const calls = new Map();
const byTool = {};
const counted = new Set();
for (const file of files) {
  for (const line of readFileSync(file, "utf8").split("\n")) {
    if (!line.trim()) continue;
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    const message = entry.message;
    if (entry.type !== "assistant" || !message?.usage) continue;
    const time = Date.parse(entry.timestamp);
    if (time > to) continue;
    counted.add(file);
    calls.set(`${file}|${message.id}`, { usage: message.usage, model: message.model });
    for (const block of message.content ?? []) {
      if (block.type === "tool_use") byTool[block.name] = (byTool[block.name] ?? 0) + 1;
    }
  }
}

const tokens = { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, cacheWrite5m: 0, cacheWrite1h: 0 };
const models = {};
for (const { usage, model } of calls.values()) {
  tokens.input += usage.input_tokens ?? 0;
  tokens.output += usage.output_tokens ?? 0;
  tokens.cacheRead += usage.cache_read_input_tokens ?? 0;
  tokens.cacheWrite += usage.cache_creation_input_tokens ?? 0;
  tokens.cacheWrite5m += usage.cache_creation?.ephemeral_5m_input_tokens ?? 0;
  tokens.cacheWrite1h += usage.cache_creation?.ephemeral_1h_input_tokens ?? 0;
  models[model] = (models[model] ?? 0) + 1;
}

// Price row: | <model> | input | output | cache read | cache write | (USD per million tokens)
let costUsd = null;
let priceTableDate = null;
const usageText = existsSync(USAGE) ? readFileSync(USAGE, "utf8") : "";
const row = usageText
  .split("\n")
  .find((line) => line.startsWith(`| ${runLog.model} |`) || line.startsWith(`| \`${runLog.model}\` |`));
if (row) {
  const [input, output, cacheRead, cacheWrite] = row
    .split("|")
    .slice(2, 6)
    .map((cell) => Number(cell.replace(/[^0-9.]/g, "")));
  if ([input, output, cacheRead, cacheWrite].every((price) => Number.isFinite(price) && price > 0)) {
    costUsd =
      Math.round(
        ((tokens.input * input +
          tokens.output * output +
          tokens.cacheRead * cacheRead +
          tokens.cacheWrite * cacheWrite) /
          1e6) *
          100,
      ) / 100;
    priceTableDate = usageText.match(/Price table date:\s*(\S+)/)?.[1] ?? null;
  }
}

const sortedTools = Object.fromEntries(Object.entries(byTool).sort((a, b) => b[1] - a[1]));
const usage = {
  inputTokens: tokens.input,
  outputTokens: tokens.output,
  cacheReadTokens: tokens.cacheRead,
  cacheWriteTokens: tokens.cacheWrite,
  modelCalls: calls.size,
  toolCalls: { total: Object.values(byTool).reduce((sum, n) => sum + n, 0), byTool: sortedTools },
  costUsd,
  priceTableDate,
};

console.log(`transcript: ${transcript}`);
for (const file of counted) {
  if (resolve(file) !== resolve(transcript)) console.log(`also counted: ${file}`);
}
console.log(`window: start of the transcript .. ${runLog.end ?? "now"}`);
console.log(`models: ${JSON.stringify(models)}`);
console.log(`cache writes: ${tokens.cacheWrite5m} at 5 minutes, ${tokens.cacheWrite1h} at 1 hour`);
console.log(JSON.stringify(usage, null, 2));
if (tokens.cacheWrite5m > 0 && costUsd !== null) {
  console.log("note: usage.md has one cache-write price; 5-minute writes are cheaper than 1-hour writes");
}

if (args.includes("--write")) {
  runLog.usage = { ...runLog.usage, ...usage };
  if (!configured) runLog.transcript = transcript;
  writeFileSync(RUN_LOG, JSON.stringify(runLog, null, 2) + "\n");
  console.log(`written to ${RUN_LOG}`);
}
