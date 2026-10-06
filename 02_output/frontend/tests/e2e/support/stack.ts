import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// KP-08: the end-to-end tests run against their own fresh compose project, never a running server.
const here = dirname(fileURLToPath(import.meta.url));
export const ENV_FILE = resolve(here, "../e2e.env");
export const COMPOSE_FILE = resolve(here, "../../../../docker-compose.yml");
export const PROJECT = "regtest-e2e";

export function e2eEnv(): Record<string, string> {
  const env: Record<string, string> = {};
  for (const line of readFileSync(ENV_FILE, "utf8").split("\n")) {
    const m = /^([A-Z_]+)=(.*)$/.exec(line.trim());
    if (m) env[m[1]] = m[2];
  }
  return env;
}

export const ENV = e2eEnv();
export const BASE_URL = `http://127.0.0.1:${ENV.FRONTEND_PORT}`;
export const MAILPIT_URL = `http://127.0.0.1:${ENV.MAILPIT_PORT}`;

export function compose(...args: string[]) {
  execFileSync(
    "docker",
    ["compose", "-p", PROJECT, "--env-file", ENV_FILE, "-f", COMPOSE_FILE, ...args],
    { stdio: "inherit", env: { ...process.env, ...ENV } },
  );
}

export function stackDefined(): boolean {
  return existsSync(COMPOSE_FILE);
}
