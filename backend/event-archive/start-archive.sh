#!/usr/bin/env bash
set -euo pipefail

args=(standalone-job --job-classname com.domain.backend.archive.RawArchiveJob)
if [[ -n "${ARCHIVE_RESTORE_PATH:-}" ]]; then
  # A missing/incompatible snapshot must fail, never silently replay from earliest.
  args+=(--fromSavepoint "$ARCHIVE_RESTORE_PATH")
fi
exec /docker-entrypoint.sh "${args[@]}"
