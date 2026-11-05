#!/bin/bash
# SSC-01 runtime regression test. Requires docker, java and a built database.jar.
# Starts run.sh in a container and probes it from a peer container on the same
# Docker network (a remote client from the DB's point of view). This also works on
# Docker Desktop, where the host cannot reach container IPs.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
JAR="$DIR/build/libs/database.jar"
IMAGE="${H2_TEST_IMAGE:-eclipse-temurin:21-jre}"
NAME="traderx-h2-ssc01-$$"
PEER="$NAME-peer"
NET="$NAME-net"
WORK="$(mktemp -d)"
fail=0
pass() { echo "PASS: $1"; }
failc() { echo "FAIL: $1"; fail=1; }
cleanup() { docker rm -f "$NAME" "$PEER" >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1; rm -rf "$WORK"; }
trap cleanup EXIT

[ -f "$JAR" ] || { echo "missing $JAR (run ./gradlew :database:jar)"; exit 2; }
command -v docker >/dev/null || { echo "docker is required"; exit 2; }

PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
PORT_BASE=$(( 20000 + RANDOM % 9000 ))

# 1. Fail closed when no password / the default password is supplied
for case in unset sa; do
  if [ "$case" = unset ]; then pw_env=(-u DATABASE_DBPASS); else pw_env=(DATABASE_DBPASS=sa); fi
  (cd "$DIR" && env "${pw_env[@]}" DATABASE_DBUSER=ssc01_test_user DATABASE_DATA_DIR="$WORK/$case" DATABASE_TCP_PORT=$PORT_BASE \
     DATABASE_PG_PORT=$((PORT_BASE+1)) DATABASE_WEB_PORT=$((PORT_BASE+2)) \
     timeout 20 bash run.sh >"$WORK/$case.log" 2>&1)
  rc=$?
  if [ $rc -ne 0 ] && [ $rc -ne 124 ]; then pass "run.sh refuses to start with DATABASE_DBPASS $case (rc=$rc)"
  else failc "run.sh started with DATABASE_DBPASS $case (rc=$rc)"; fi
done

# 2. Start the DB in a container with an operator-supplied password
docker network create "$NET" >/dev/null || { echo "docker network create failed"; exit 2; }
docker run -d --name "$NAME" --network "$NET" --network-alias db -v "$DIR":/database:ro -w /database \
  -e DATABASE_DBUSER=ssc01_test_user -e DATABASE_DBPASS="$PASSWORD" -e DATABASE_DATA_DIR=/tmp/_data "$IMAGE" bash run.sh >/dev/null || { echo "docker run failed"; exit 2; }
for _ in $(seq 1 60); do
  docker logs "$NAME" 2>&1 | grep -q 'TCP server running' && break
  sleep 1
done
docker logs "$NAME" 2>&1 | grep -q 'TCP server running' || { docker logs "$NAME"; echo "DB did not start"; exit 2; }
docker run -d --name "$PEER" --network "$NET" -v "$DIR":/database:ro "$IMAGE" sleep infinity >/dev/null || { echo "peer container failed"; exit 2; }
IP=db
echo "DB container $NAME reachable as '$IP' from peer $PEER"

peer() { docker exec "$PEER" "$@"; }
jdbc() { peer bash -c 'echo "SELECT COUNT(*) FROM ACCOUNTS;" > /tmp/q.sql && java -cp /database/build/libs/database.jar org.h2.tools.RunScript -url "$1" -user "$2" -password "$3" -script /tmp/q.sql' _ "$@" >"$WORK/jdbc.log" 2>&1; }

if jdbc "jdbc:h2:tcp://$IP:18082/traderx" ssc01_test_user "$PASSWORD"; then pass "legitimate TCP login with the configured password works"
else cat "$WORK/jdbc.log"; failc "legitimate TCP login failed"; fi

if jdbc "jdbc:h2:tcp://$IP:18082/traderx" sa sa; then failc "default sa/sa credentials accepted over TCP"
else pass "default sa/sa credentials denied over TCP"; fi

if jdbc "jdbc:h2:tcp://$IP:18082/ssc01probe" sa sa; then failc "remote client could create a new database"
else pass "remote client cannot create a new database"; fi

web="$(peer curl -s -m 5 -H 'Host: localhost' "http://$IP:18084/" 2>/dev/null)"
if [ -z "$web" ] || echo "$web" | grep -q "remote connections ('webAllowOthers') are disabled"; then
  pass "H2 web console refuses a remote client"
else failc "H2 web console served to a remote client"; fi

pg="$(peer timeout 5 bash -c "exec 3<>/dev/tcp/$IP/18083 && printf '\x00\x00\x00\x08\x04\xd2\x16\x2f' >&3 && head -c 1 <&3" 2>/dev/null)"
if [ -n "$pg" ]; then failc "PG server answered a remote client"
else pass "PG server does not answer a remote client"; fi

if [ "$fail" -ne 0 ]; then echo "SSC-01 runtime access test FAILED"; exit 1; fi
echo "SSC-01 runtime access test PASSED"
