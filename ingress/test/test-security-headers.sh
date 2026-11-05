#!/usr/bin/env bash
# Builds the ingress image and asserts edge security headers are present.
# Upstreams are mapped to 127.0.0.1, so proxied routes answer 502; headers must still be set.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE=traderx-ingress-test
NAME=traderx-ingress-test-$$
PORT=${INGRESS_TEST_PORT:-18880}
HOSTS=""
for h in database reference-data web-front-end-angular trade-feed people-service \
         account-service position-service trade-service trade-processor; do
  HOSTS="$HOSTS --add-host=$h:127.0.0.1"
done

docker build -q -t "$IMAGE" . >/dev/null
# shellcheck disable=SC2086
docker run -d --rm --name "$NAME" $HOSTS -p "$PORT:8080" "$IMAGE" >/dev/null
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

for _ in $(seq 1 30); do
  curl -s -o /dev/null "http://localhost:$PORT/" && break
  sleep 0.5
done

fail=0
check() { # path, header regex
  local headers
  headers=$(curl -s -D - -o /dev/null "http://localhost:$PORT$1")
  if echo "$headers" | grep -qiE "$2"; then
    echo "PASS $1 has /$2/"
  else
    echo "FAIL $1 missing /$2/"; fail=1
  fi
}
check_absent() { # path, header regex
  local headers
  headers=$(curl -s -D - -o /dev/null "http://localhost:$PORT$1")
  if echo "$headers" | grep -qiE "$2"; then
    echo "FAIL $1 leaks /$2/"; fail=1
  else
    echo "PASS $1 does not send /$2/"
  fi
}

for path in / /account-service/ /does-not-exist; do
  check "$path" '^X-Content-Type-Options: nosniff'
  check "$path" '^X-Frame-Options: DENY'
  check "$path" '^Referrer-Policy: strict-origin-when-cross-origin'
  check "$path" "^Content-Security-Policy: .*default-src 'self'"
  check "$path" "^Content-Security-Policy: .*script-src 'self';"
  check "$path" "^Content-Security-Policy: .*frame-ancestors 'none'"
  check "$path" "^Content-Security-Policy: .*object-src 'none'"
  check_absent "$path" '^Server: nginx/[0-9]'
done

echo "--- sample response headers for /"
curl -s -D - -o /dev/null "http://localhost:$PORT/"
exit $fail
