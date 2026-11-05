#!/bin/bash
# SSC-01 regression test: the H2 web console and PG server must not accept
# remote clients, and the web console port must not be published.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
fail=0

check_absent() {
  local file="$1" pattern="$2" msg="$3"
  if grep -nE -- "$pattern" "$file"; then
    echo "FAIL: $msg ($file)"
    fail=1
  else
    echo "PASS: $msg"
  fi
}

check_absent "$DIR/run.sh" '-webAllowOthers' "run.sh does not start the web console with -webAllowOthers"
check_absent "$DIR/run.sh" '-pgAllowOthers' "run.sh does not start the PG server with -pgAllowOthers"
check_absent "$DIR/Dockerfile" 'EXPOSE[[:space:]]+18084' "database Dockerfile does not expose the web console port"
if [ -f "$ROOT/docker-compose.yml" ]; then
  check_absent "$ROOT/docker-compose.yml" '18084' "docker-compose does not publish the web console port"
fi
if [ -f "$ROOT/ingress/nginx.traderx.conf.template" ]; then
  check_absent "$ROOT/ingress/nginx.traderx.conf.template" 'database:18084' "nginx ingress does not proxy the web console"
fi

if [ -f "$ROOT/gitops/base/ingress/ingress.yaml" ]; then
  check_absent "$ROOT/gitops/base/ingress/ingress.yaml" '/db-web' "k8s ingress does not route to the web console"
fi

if [ "$fail" -ne 0 ]; then
  echo "SSC-01 remote access test FAILED"
  exit 1
fi
echo "SSC-01 remote access test PASSED"
