import { execFileSync, spawn } from "node:child_process";
import {
  mkdtempSync,
  mkdirSync,
  copyFileSync,
  writeFileSync,
  openSync,
} from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { STATE_FILE, type StackState } from "./stack";

const FRONTEND = resolve(import.meta.dirname, "..", "..");
const BACKEND = resolve(FRONTEND, "..", "backend");
const DB_PASSWORD = "e2e-db-password";

function freePort(): Promise<number> {
  return new Promise((done, fail) => {
    const server = createServer();
    server.once("error", fail);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      const port = typeof address === "object" && address ? address.port : 0;
      server.close(() => done(port));
    });
  });
}

function docker(...args: string[]): string {
  return execFileSync("docker", args, { encoding: "utf-8" }).trim();
}

function mappedPort(container: string, port: number): string {
  return docker("port", container, String(port))
    .split("\n")[0]
    .split(":")
    .pop() as string;
}

async function waitFor(
  url: string,
  accept: (status: number) => boolean,
  what: string,
) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (accept(response.status)) return;
    } catch {
      // not up yet
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`${what} did not start: ${url}`);
}

export default async function globalSetup() {
  const workDir = mkdtempSync(join(tmpdir(), "registration-e2e-"));
  const jsonCopyDir = join(workDir, "json-copies");
  mkdirSync(jsonCopyDir);
  const conferenceFile = join(workDir, "conference.json");
  copyFileSync(
    join(import.meta.dirname, "..", "fixtures", "conference.json"),
    conferenceFile,
  );

  const postgres = docker(
    "run",
    "-d",
    "--rm",
    "-e",
    "POSTGRES_USER=registration",
    "-e",
    `POSTGRES_PASSWORD=${DB_PASSWORD}`,
    "-e",
    "POSTGRES_DB=registration",
    "-p",
    "127.0.0.1::5432",
    "postgres:16.15-alpine",
  );
  const mailpit = docker(
    "run",
    "-d",
    "--rm",
    "-p",
    "127.0.0.1::1025",
    "-p",
    "127.0.0.1::8025",
    "axllent/mailpit:v1.31.1",
  );
  const state: StackState = {
    frontendUrl: "",
    backendUrl: "",
    mailpitUrl: `http://127.0.0.1:${mappedPort(mailpit, 8025)}`,
    jsonCopyDir,
    organizerUser: "organizer",
    organizerPassword: "e2e-organizer-pass-00000001",
    conferenceName: "Konferenca 2026",
    containers: [postgres, mailpit],
    pids: [],
    workDir,
  };
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
  for (let i = 0; i < 60; i++) {
    try {
      docker(
        "exec",
        postgres,
        "pg_isready",
        "-U",
        "registration",
        "-d",
        "registration",
      );
      break;
    } catch {
      await new Promise((r) => setTimeout(r, 500));
    }
  }

  execFileSync("./mvnw", ["-B", "-q", "-DskipTests", "package"], {
    cwd: BACKEND,
    stdio: "inherit",
  });
  const backendPort = await freePort();
  const backendLog = openSync(join(workDir, "backend.log"), "w");
  const backend = spawn(
    "java",
    ["-jar", "target/registration-backend-0.1.0.jar"],
    {
      cwd: BACKEND,
      detached: true,
      stdio: ["ignore", backendLog, backendLog],
      env: {
        ...process.env,
        SERVER_PORT: String(backendPort),
        APP_ENVIRONMENT: "test",
        DATABASE_URL: `jdbc:postgresql://127.0.0.1:${mappedPort(postgres, 5432)}/registration`,
        DATABASE_USER: "registration",
        POSTGRES_PASSWORD: DB_PASSWORD,
        SMTP_HOST: "127.0.0.1",
        SMTP_PORT: mappedPort(mailpit, 1025),
        SMTP_TLS: "false",
        SMTP_USERNAME: "",
        SMTP_PASSWORD: "",
        MAIL_FROM: "registration@konferenca.test",
        CONFERENCE_NAME: state.conferenceName,
        ORGANIZER_EMAILS: "organizer1@konferenca.test",
        ORGANIZER_USERNAME: state.organizerUser,
        ORGANIZER_PASSWORD: state.organizerPassword,
        CONFERENCE_CONFIG_PATH: conferenceFile,
        JSON_COPY_DIR: jsonCopyDir,
        RECAPTCHA_TEST_MODE: "true",
        RECAPTCHA_SITE_KEY: "",
        RECAPTCHA_SECRET_KEY: "",
        RATE_LIMIT_REGISTRATIONS_PER_MINUTE: "10000",
        RATE_LIMIT_EXPORTS_PER_MINUTE: "10000",
        RATE_LIMIT_FORMS_PER_MINUTE: "10000",
      },
    },
  );
  state.pids.push(backend.pid as number);
  state.backendUrl = `http://127.0.0.1:${backendPort}`;
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
  await waitFor(`${state.backendUrl}/actuator/health`, () => true, "backend");

  const frontendPort = await freePort();
  const frontendLog = openSync(join(workDir, "frontend.log"), "w");
  const vite = spawn(
    "npx",
    [
      "vite",
      "--host",
      "127.0.0.1",
      "--port",
      String(frontendPort),
      "--strictPort",
    ],
    {
      cwd: FRONTEND,
      detached: true,
      stdio: ["ignore", frontendLog, frontendLog],
      env: { ...process.env, API_PROXY_TARGET: state.backendUrl },
    },
  );
  state.pids.push(vite.pid as number);
  state.frontendUrl = `http://127.0.0.1:${frontendPort}`;
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
  await waitFor(state.frontendUrl, (s) => s === 200, "frontend");
}
