#!/usr/bin/env bash
# The only way a run touches `.env` (rules.md, "Secrets"). The agent never reads `.env` itself:
# it calls this script, which reads the file and prints key names and states, never a value.
#
#   bash 01_input/00_general/tools/secrets.sh check
#       Every key of secrets.env.example: present, missing, or not required. Exit 1 when a
#       required key is missing, 2 when .env does not exist.
#   bash 01_input/00_general/tools/secrets.sh run KEY[,KEY...] -- <command...>
#       Runs one allowed command with only the named keys in its environment. Allowed commands:
#       `docker compose ...`, `docker run ...`, `./mvnw ...` or `mvnw ...` (from the current
#       directory), `npx playwright ...`. Compose reads `${KEY}` from that environment, so no
#       --env-file is needed. Arguments that could print an environment are refused.
#   bash 01_input/00_general/tools/secrets.sh leak-check
#       Phase 6: names of tracked or untracked files that contain a value of .env, and the number
#       of commit-message lines that contain one (values of at least 6 characters). Exit 1 when
#       anything is found.
#
# This file belongs to the template; a run never changes it (input manifest).

set -u

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
ENV_FILE="$ROOT/.env"
EXAMPLE="$ROOT/01_input/01_project/secrets.env.example"

die() {
  echo "secrets.sh: $*" >&2
  exit 3
}

[ -f "$EXAMPLE" ] || die "secrets.env.example not found"

# Key names of secrets.env.example with "required" or "optional" from the comment above them
# ("test-mode only" and "not needed" are optional, everything else is required).
declared_keys() {
  awk '
    /^#/ { comment = $0; next }
    /^[A-Z][A-Z0-9_]*=/ {
      key = substr($0, 1, index($0, "=") - 1)
      kind = (comment ~ /test-mode only|not needed/) ? "optional" : "required"
      print key, kind
    }
  ' "$EXAMPLE" | tr -d '\r'
}

is_declared() {
  declared_keys | awk -v k="$1" '$1 == k { found = 1 } END { exit found ? 0 : 1 }'
}

# The value of a key: last assignment in .env, without carriage return or surrounding quotes.
value_of() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r' | sed -e 's/^"\(.*\)"$/\1/' -e "s/^'\(.*\)'$/\1/"
}

# A value that is empty or only blanks or quotes counts as missing.
is_filled() {
  value_of "$1" | tr -d ' "'"'" | grep -q .
}

cmd_check() {
  [ -f "$ENV_FILE" ] || { echo ".env: missing (copy secrets.env.example to .env in the repository root)"; exit 2; }
  local missing=0 key kind
  while read -r key kind; do
    if is_filled "$key"; then
      echo "$key: present"
    elif [ "$kind" = required ]; then
      echo "$key: missing"
      missing=1
    else
      echo "$key: not required (empty)"
    fi
  done < <(declared_keys)
  exit "$missing"
}

cmd_run() {
  [ -f "$ENV_FILE" ] || die ".env not found"
  [ $# -ge 3 ] && [ "$2" = "--" ] || die "usage: secrets.sh run KEY[,KEY...] -- <command...>"
  local keys="$1"
  shift 2

  case "$1 ${2:-}" in
    "docker compose" | "docker run" | "./mvnw "* | "mvnw "* | "npx playwright") ;;
    *) die "command not allowed: $1 ${2:-}" ;;
  esac
  local arg
  for arg in "$@"; do
    # Anything that could print the environment or the file is refused.
    if printf '%s\n' "$arg" | grep -qiwE 'env|printenv|set|export|declare|echo|printf|config|proc|environ' \
      || printf '%s\n' "$arg" | grep -qF '.env'; then
      die "argument not allowed: $arg"
    fi
  done

  local key
  local -a assignments=()
  for key in ${keys//,/ }; do
    is_declared "$key" || die "unknown key: $key"
    is_filled "$key" || die "key has no value in .env: $key"
    assignments+=("$key=$(value_of "$key")")
  done
  exec env "${assignments[@]}" "$@"
}

cmd_leak_check() {
  [ -f "$ENV_FILE" ] || die ".env not found"
  local found=0 lines=0 key value count file
  echo "files containing a value of .env, as <file>: <key> (none expected):"
  while read -r key; do
    value="$(value_of "$key" | tr -d ' "'"'")"
    [ "${#value}" -ge 6 ] || continue
    # The key name tells a reader whether a match is a real leak or a common word.
    while IFS= read -r -d '' file; do
      [ "$file" = ".env" ] && continue
      if grep -qF -- "$value" "$ROOT/$file" 2>/dev/null; then
        echo "$file: $key"
        found=1
      fi
    done < <(cd "$ROOT" && git ls-files -co --exclude-standard -z)
    count="$(cd "$ROOT" && git log --all --format=%B | grep -cF -- "$value")"
    if [ "$count" != 0 ]; then
      echo "commit messages: $key ($count lines)"
      lines=$((lines + count))
    fi
  done < <(sed -n 's/^\([A-Z][A-Z0-9_]*\)=.*/\1/p' "$ENV_FILE" | tr -d '\r' | sort -u)
  echo "commit-message lines containing a value of .env (0 expected): $lines"
  [ "$lines" = 0 ] || found=1
  exit "$found"
}

case "${1:-}" in
  check) cmd_check ;;
  run) shift; cmd_run "$@" ;;
  leak-check) cmd_leak_check ;;
  *) die "usage: secrets.sh check | run KEY[,KEY...] -- <command...> | leak-check" ;;
esac
