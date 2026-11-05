#!/usr/bin/env bash
set -uo pipefail
mode=${1:-}
if [[ -n "$mode" && "$mode" != --runtime ]]; then
  echo "FAIL: usage: $0 [--runtime]"
  exit 1
fi
failed=0
check() {
  if "$@"; then echo "PASS: $*"; else echo "FAIL: $*"; failed=1; fi
}
contexts=(database reference-data trade-feed people-service account-service position-service trade-service trade-processor web-front-end/angular ingress)
nonroot() {
  awk 'toupper($1) == "FROM" {user=""} toupper($1) == "USER" {user=$2}
    END {exit !(user != "" && user !~ /^(root|0)(:|$)/)}' "$1"
}
safe_dockerfiles() {
  ! find . -type f \( -name Dockerfile -o -name base.Dockerfile \) -not -path '*/node_modules/*' -not -path './.git/*' -exec \
    grep -En 'sudo|^FROM (node:(14|16|18)([-.:[:space:]]|$)|nginx(:latest)?([[:space:]]|$))|^ENTRYPOINT.*(gradlew.*bootRun|dotnet run|ng serve)' {} +
}
runtime_base() {
  ! awk 'toupper($1) == "FROM" {base=$2} END {print base}' "$1" | grep -q mcr.microsoft.com/vscode/devcontainers
}
compose_hardened() {
  docker compose config --format json | python3 -c '
import json,sys
c=json.load(sys.stdin)
ok=True
for name,s in c["services"].items():
    good=s.get("read_only") is True and any(x.replace("=",":") == "no-new-privileges:true" for x in s.get("security_opt",[]))
    good=good and not any(v.get("target")=="/workspace" for v in s.get("volumes",[]))
    print(("PASS" if good else "FAIL")+": compose "+name)
    ok=ok and good
sys.exit(not ok)'
}
check safe_dockerfiles
for context in "${contexts[@]}"; do
  check nonroot "$context/Dockerfile"
  check runtime_base "$context/Dockerfile"
done
check compose_hardened
if [[ "$mode" == --runtime ]]; then
  for context in "${contexts[@]}"; do
    image="hardening-${context//\//-}"
    args=()
    if [[ -n "${GRADLE_INIT_SECRET:-}" ]]; then
      args+=(--secret "id=gradle_init,src=$GRADLE_INIT_SECRET")
    fi
    check docker build "${args[@]}" -t "$image" "$context"
    if uid=$(docker run --rm --entrypoint id "$image" -u); then
      echo "id $image: $uid"
      check test "$uid" -ne 0
    else
      echo "FAIL: id $image"
      failed=1
    fi
  done
fi
exit "$failed"
