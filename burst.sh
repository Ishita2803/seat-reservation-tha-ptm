#!/usr/bin/env bash
set -euo pipefail

# Against a local compose stack, use http://host.docker.internal:8080, not
# localhost -- the k6 container's localhost is itself, not your machine.
BASE_URL="${1:-http://host.docker.internal:8080}"
ADMIN_SECRET="${2:-${ADMIN_SECRET:-dev-admin-secret}}"

echo "Running burst against $BASE_URL"

docker run --rm -i \
  -e BASE_URL="$BASE_URL" \
  -e ADMIN_SECRET="$ADMIN_SECRET" \
  -v "$(pwd)/burst:/scripts" \
  grafana/k6 run /scripts/burst.js
