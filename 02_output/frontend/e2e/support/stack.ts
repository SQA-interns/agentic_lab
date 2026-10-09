import { readFileSync } from "node:fs";
import { join } from "node:path";

/** Addresses of the fresh stack started by global-setup.ts. */
export interface StackState {
  frontendUrl: string;
  backendUrl: string;
  mailpitUrl: string;
  jsonCopyDir: string;
  organizerUser: string;
  organizerPassword: string;
  conferenceName: string;
  containers: string[];
  pids: number[];
  workDir: string;
}

export const STATE_FILE = join(import.meta.dirname, "..", ".state.json");

export function stack(): StackState {
  return JSON.parse(readFileSync(STATE_FILE, "utf-8")) as StackState;
}
