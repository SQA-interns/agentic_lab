#!/usr/bin/env bash
# Writes or verifies the SHA-256 manifests (sha256sum format, LF-normalised content).
#   tools/manifests.sh write-acceptance   write docs/03_acceptance-manifest.sha256 (phase 3 only)
#   tools/manifests.sh verify             recompute both manifests and report mismatches
set -euo pipefail
OUT="$(cd "$(dirname "$0")/.." && pwd)"   # 02_output
ROOT="$(cd "$OUT/.." && pwd)"             # repository root

frozen_files() {
  cd "$OUT"
  {
    find backend/src/test/java/lab/conference/acceptance -type f
    find backend/src/test/resources/acceptance -type f
    find frontend/e2e -type f
    echo frontend/playwright.config.ts
  } | LC_ALL=C sort
}

hash_lf() { tr -d '\r' < "$1" | sha256sum | cut -d' ' -f1; }

check() { # $1 manifest, $2 base dir
  local manifest="$1" base="$2" bad=0 n=0
  while read -r sum path; do
    n=$((n + 1))
    if [[ ! -f "$base/$path" ]]; then echo "MISSING  $path"; bad=$((bad + 1)); continue; fi
    if [[ "$(hash_lf "$base/$path")" != "$sum" ]]; then echo "MISMATCH $path"; bad=$((bad + 1)); fi
  done < "$manifest"
  echo "$(basename "$manifest"): $n entries, $bad problem(s)"
  return $bad
}

case "${1:-verify}" in
  write-acceptance)
    frozen_files | while read -r f; do printf '%s  %s\n' "$(hash_lf "$OUT/$f")" "$f"; done \
      > "$OUT/docs/03_acceptance-manifest.sha256"
    wc -l < "$OUT/docs/03_acceptance-manifest.sha256"
    ;;
  verify)
    status=0
    check "$OUT/docs/03_acceptance-manifest.sha256" "$OUT" || status=1
    check "$OUT/docs/00_input-manifest.sha256" "$ROOT" || status=1
    # Files added under the frozen paths after phase 3 would not be listed in the manifest.
    extra=$(comm -13 <(cut -d' ' -f3 "$OUT/docs/03_acceptance-manifest.sha256" | LC_ALL=C sort) <(frozen_files))
    if [[ -n "$extra" ]]; then echo "UNLISTED files under frozen paths:"; echo "$extra"; status=1; fi
    exit $status
    ;;
  *) echo "usage: $0 write-acceptance|verify" >&2; exit 2 ;;
esac
