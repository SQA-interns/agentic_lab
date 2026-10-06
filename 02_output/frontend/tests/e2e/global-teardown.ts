import { compose, stackDefined } from "./support/stack";

export default function globalTeardown() {
  if (stackDefined() && process.env.E2E_KEEP_STACK !== "1") {
    compose("down", "-v", "--remove-orphans");
  }
}
