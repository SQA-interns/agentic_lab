import { execFileSync } from "node:child_process";
import { existsSync, rmSync } from "node:fs";
import { STATE_FILE, stack } from "./stack";

export default async function globalTeardown() {
  if (!existsSync(STATE_FILE)) return;
  const state = stack();
  for (const pid of state.pids) {
    try {
      process.kill(-pid, "SIGTERM");
    } catch {
      // already stopped
    }
  }
  for (const container of state.containers) {
    try {
      execFileSync("docker", ["rm", "-f", container], { stdio: "ignore" });
    } catch {
      // already removed
    }
  }
  rmSync(state.workDir, { recursive: true, force: true });
  rmSync(STATE_FILE, { force: true });
}
