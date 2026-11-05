#!/usr/bin/env bash
# Fails if internal-only services are routed by a public ingress.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
files=("$root/ingress/nginx.traderx.conf.template" "$root/gitops/base/ingress/ingress.yaml")
status=0
for f in "${files[@]}"; do
  if grep -nE 'trade-processor' "$f"; then
    echo "FAIL: $f routes the internal trade-processor service"
    status=1
  else
    echo "PASS: $f has no trade-processor route"
  fi
done
exit $status
