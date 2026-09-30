// Minimal typing for environment access in config and e2e files (no @types/node in tech-stack.md).
declare const process: { env: Record<string, string | undefined> };
