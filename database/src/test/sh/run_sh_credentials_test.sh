#!/bin/bash
# SSC-03: runtime database credentials must be supplied by the operator.
set -u
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
fail=0

for state in unset empty; do
  if [ "$state" = unset ]; then user_env=(-u DATABASE_DBUSER); else user_env=(DATABASE_DBUSER=); fi
  output=$(env "${user_env[@]}" DATABASE_DBPASS=SSC03-regression-only-strong-password DATABASE_H2JAR=/nonexistent/ssc03.jar bash "$DIR/run.sh" 2>&1)
  rc=$?
  if [ "$rc" -eq 1 ] && [[ "$output" == *'ERROR: DATABASE_DBUSER must be set'* ]]; then
    echo "PASS: run.sh rejects $state DATABASE_DBUSER before starting Java"
  else
    echo "FAIL: run.sh did not reject $state DATABASE_DBUSER with a clear error (rc=$rc)"
    fail=1
  fi
done

if grep -nE 'DATABASE_DBUSER(:[^}]*|=).*sa' "$DIR/run.sh"; then
  echo 'FAIL: run.sh contains a database username default'
  fail=1
else
  echo 'PASS: run.sh has no database username default'
fi

while IFS= read -r file; do
  if grep -nE 'DATABASE_DB(USER|PASS):[^}]*}' "$file"; then
    echo "FAIL: credential fallback in ${file#"$ROOT/"}"
    fail=1
  else
    echo "PASS: no credential fallback in ${file#"$ROOT/"}"
  fi
done < <(find "$ROOT" -path '*/src/main/resources/application.properties' -type f | sort)

if [ "$fail" -ne 0 ]; then
  echo 'SSC-03 credentials test FAILED'
  exit 1
fi
echo 'SSC-03 credentials test PASSED'
