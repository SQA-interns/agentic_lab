// Shared settings of the end-to-end stack: its own compose project, ports and credentials.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";

export const E2E_FRONTEND_PORT = 18081;
export const E2E_MAILPIT_PORT = 18025;
export const OUT_DIR = resolve(fileURLToPath(new URL(".", import.meta.url)), "../../..");
const STATE_FILE = join(tmpdir(), "registration-e2e-state.json");

export interface StackState {
  project: string;
  envFile: string;
  organizerUsername: string;
  organizerPassword: string;
  organizerEmail: string;
}

export function newState(): StackState {
  const dir = mkdtempSync(join(tmpdir(), "registration-e2e-"));
  const state: StackState = {
    project: `registration-e2e-${randomBytes(4).toString("hex")}`,
    envFile: join(dir, "e2e.env"),
    organizerUsername: "e2e-organizer",
    organizerPassword: randomBytes(18).toString("hex"),
    organizerEmail: "e2e-organizer@konferenca.test",
  };
  writeFileSync(
    state.envFile,
    [
      `POSTGRES_PASSWORD=${randomBytes(18).toString("hex")}`,
      `ORGANIZER_USERNAME=${state.organizerUsername}`,
      `ORGANIZER_PASSWORD=${state.organizerPassword}`,
      `ORGANIZER_EMAILS=${state.organizerEmail}`,
      `FRONTEND_PORT=${E2E_FRONTEND_PORT}`,
      `MAILPIT_PORT=${E2E_MAILPIT_PORT}`,
      "CONFERENCE_NAME=E2E Konferenca",
      "",
    ].join("\n"),
    { mode: 0o600 },
  );
  writeFileSync(STATE_FILE, JSON.stringify(state), { mode: 0o600 });
  return state;
}

export function loadState(): StackState {
  return JSON.parse(readFileSync(STATE_FILE, "utf8")) as StackState;
}

export function clearState(state: StackState): void {
  rmSync(resolve(state.envFile, ".."), { recursive: true, force: true });
  rmSync(STATE_FILE, { force: true });
}

export function compose(state: StackState, ...args: string[]): void {
  execFileSync(
    "docker",
    [
      "compose",
      "--project-directory",
      OUT_DIR,
      "-f",
      join(OUT_DIR, "docker-compose.yml"),
      "-p",
      state.project,
      "--env-file",
      state.envFile,
      ...args,
    ],
    { stdio: "inherit", timeout: 600_000 },
  );
}
