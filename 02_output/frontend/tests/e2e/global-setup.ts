import { execFileSync } from "node:child_process";
import { join } from "node:path";
import { OUT_DIR, compose, newState } from "./stack";

// Builds the backend jar and starts a fresh, uniquely named stack (KP-08).
export default function globalSetup(): void {
  execFileSync("./mvnw", ["-B", "-q", "-ntp", "package", "-DskipTests"], {
    cwd: join(OUT_DIR, "backend"),
    stdio: "inherit",
    timeout: 600_000,
  });
  const state = newState();
  compose(state, "up", "-d", "--build", "--wait", "--wait-timeout", "300");
}
