import { clearState, compose, loadState } from "./stack";

// Removes the stack, its volumes and the generated credentials.
export default function globalTeardown(): void {
  const state = loadState();
  try {
    compose(state, "down", "-v", "--remove-orphans");
  } finally {
    clearState(state);
  }
}
