#!/usr/bin/env bash
# Prints a {bcrypt} hash for ORGANIZER_PASSWORD_HASH. The password is read from the terminal
# (not echoed) and passed on stdin to the backend image's PasswordHashCli.
# Usage: tools/hash-password.sh   (builds agenticlab-backend:local first if it is missing)
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE=agenticlab-backend:local
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  docker build -q -t "$IMAGE" backend >/dev/null
fi
if [[ -t 0 ]]; then
  read -r -s -p "Organizer password (min 12 characters): " PASSWORD; echo >&2
else
  read -r PASSWORD
fi
printf '%s\n' "$PASSWORD" | docker run --rm -i --entrypoint java "$IMAGE" \
  -Dloader.main=lab.conference.platform.PasswordHashCli \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
