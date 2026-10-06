import { compose, stackDefined } from "./support/stack";

export default function globalSetup() {
  if (!stackDefined()) {
    console.warn("docker-compose.yml not found: the stack under test does not exist yet");
    return;
  }
  compose("down", "-v", "--remove-orphans");
  compose("up", "-d", "--build", "--wait", "--wait-timeout", "300");
}
