#!/usr/bin/env bash
set -euo pipefail
args=(standalone-job --job-classname com.domain.backend.analyticsjob.AnalyticsJob)
if [[ -n "${ANALYTICS_RESTORE_PATH:-}" ]]; then
  args+=(--fromSavepoint "$ANALYTICS_RESTORE_PATH")
fi
exec /docker-entrypoint.sh "${args[@]}"
