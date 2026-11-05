#!/bin/bash

### Fetch variables
set -a
: ${DATABASE_TCP_PORT:=18082}
: ${DATABASE_PG_PORT:=18083}
: ${DATABASE_WEB_PORT:=18084}
: ${DATABASE_H2JAR:=./build/libs/database.jar}
: ${DATABASE_DATA_DIR:=./_data}
: ${DATABASE_DBNAME:=traderx}
: ${DATABASE_HOSTNAME:=$HOSTNAME}
: ${DATABASE_JDBC_URL:="jdbc:h2:tcp://$HOSTNAME:$DATABASE_TCP_PORT/$DATABASE_DBNAME"}

set +a

if [ -z "${DATABASE_DBUSER:-}" ]; then
  echo 'ERROR: DATABASE_DBUSER must be set to a non-empty username (see database/README.md). Refusing to start.' >&2
  exit 1
fi

### Fail closed: refuse to start without an operator-supplied, non-default password (SSC-01)
if [ -z "${DATABASE_DBPASS:-}" ] || [ "$DATABASE_DBPASS" = "sa" ]; then
  echo 'ERROR: DATABASE_DBPASS must be set to a non-default password (see database/README.md). Refusing to start.' >&2
  exit 1
fi

### Start the DB
echo "Data will be located in $DATABASE_DATA_DIR"
echo "Database name is $DATABASE_DBNAME"
echo 'Running schema setup script with log output to stdout below'
echo '---------------------------------------------------------------------------'
java -cp $DATABASE_H2JAR org.h2.tools.RunScript -url "jdbc:h2:$DATABASE_DATA_DIR/$DATABASE_DBNAME;DATABASE_TO_UPPER=TRUE;TRACE_LEVEL_SYSTEM_OUT=3" -user "$DATABASE_DBUSER" -password "$DATABASE_DBPASS" -script initialSchema.sql || { echo "ERROR: schema setup failed. Refusing to start." >&2; exit 1; }
# TCP accepts remote clients because the other TraderX containers connect over JDBC.
# The PG server and web console are bound to local connections only (SSC-01).
echo 'Starting Database Server - DB logs below'
echo '---------------------------------------------------------------------------'
exec java -jar $DATABASE_H2JAR -pg -pgPort $DATABASE_PG_PORT -baseDir $DATABASE_DATA_DIR -tcp -tcpPort $DATABASE_TCP_PORT -tcpAllowOthers -web -webPort $DATABASE_WEB_PORT