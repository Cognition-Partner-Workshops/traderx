# FINOS | TraderX Sample Trading App | Database

![DEV Only Warning](https://badgen.net/badge/warning/not-for-production/red) ![Local Dev Machine Supported](http://badgen.net/badge/local-dev/supported/green)

## Introduction

This is designed to play the role of a SQL database that runs standalone as part of an example environment. The other processes in this environment interact with this via SQL / JDBC drivers and therefore this component can be swapped out in other iterations of this environment and replaced with a robust and productionizable RDBMS.

This uses H2 Java-based database as a standalone server and initializes with an empty SQL schema every time it is started. It requires a password (see below) and refuses to start without one.

## Default Port Numbers
| Protocol | Port Number |
| :--- | :--- |
| TCP | 18082 |
| PG | 18083 |
| HTTP | 18084 |
 
## Connecting to this database remotely
You can use the `$DATABASE_TCP_PORT`  or `$DATABASE_PG_PORT` and the database URL in JDBC is `jdbc:h2:./_data/traderx`

The username defaults to `sa`; the password is whatever you set in `DATABASE_DBPASS`.

## Connecting to the web console
You can use the `$DATABASE_HTTP_PORT`  or `$DATABASE_PG_PORT` and the database URL in JDBC is `jdbc:h2:traderx` (This is because -baseDir is already set to ./_data) - NOTE you will have to change the default setting in the web console which often uses a home directory path. 

The username defaults to `sa`; the password is whatever you set in `DATABASE_DBPASS`.

The database you want to use in the H2 GUI is `./traderx` (This may not be the default listed in the GUI)

## Credentials and network exposure (SSC-01)
`run.sh` fails closed: it exits with an error unless `DATABASE_DBPASS` is set to a non-empty value other than the well-known default `sa`. No password is committed to the repo; the operator chooses one:

* docker-compose: put `DATABASE_DBPASS=<your password>` in a `.env` file next to `docker-compose.yml` (`.env` is git-ignored) or export it. Compose refuses to start without it and passes it to `database`, `account-service`, `position-service` and `trade-processor`.
* Kubernetes (`gitops/`): create the secret before deploying: `kubectl create secret generic traderx-database --from-literal=password=<your password>`.
* Radius: pass `--parameters databasePassword=<your password>`.

Only the TCP server (`$DATABASE_TCP_PORT`, default 18082) accepts remote connections, because the other TraderX services connect to it over JDBC (`jdbc:h2:tcp://database:18082/traderx`) with the password above. The PG server and the web console only accept connections from the same host/container and their ports are not published by docker-compose, the Dockerfile, Kubernetes or Radius. To use the web console, run it from inside the container or port-forward to it.

Regression tests: `./gradlew :database:remoteAccessTest` (static, part of `check`) and `./gradlew :database:runtimeAccessTest` (needs Docker: starts the DB in a container and checks from the host that default credentials are denied, remote web console/PG are refused and a legitimate TCP login works).

## Output Directory
Data is stored in the local `./_data` directory from where the script is run. This is .gitignore'd 

## Building

This builds in gradle to retrieve H2.

```shell
$> gradle build
```

## Running on Linux

This is desinged to run on Linux but can easily run on Windows as well. It launches a DB Script runner to pre-populate the database schema- delayed by 20 seconds, and then runs the database server in the foreground.

Currently the run script has environment variables defined for versions of H2 and common ports. These should be altered (or made configurable outside the script) as the environment matures.

To launch, all that should be needed is running:
```shell
$> run.sh
```

## Running from your local Win10 Machine

You CAN run this on windows, with the help of Powershell.  All you need to do is have Java on your path, and then enter bash to run the script.  First launch a Terminal/Command Console in Windows.

```
>bash
>$ . run.sh
```

You will see the following output

```

Runing startup script
Web Console server running at http://[your IP]:18084 (only local connections)
finished startup script
TCP server running at tcp://[your IP]:18082 (others can connect)
PG server running at pg://[your IP]:18083 (only local connections)
```

On my windows PC, it actually doesn't work with the public IP address. Just change the above URLs/Host Addresses to use `localhost`  and they work fine.

Example: http://localhost:18084 


## Accessing the Web Console

1. Visit http://localhost:18084 or whatever port you run your service on
2. You should see a web console, prompting you for a username, password, and JDBC URL - Values might not be auto-populated correctly
3. Username should be `sa` and the password your `DATABASE_DBPASS` and the JDBC URL should be `jdbc:h2:./traderx` and the Driver should be `Generic H2 Embedded`
4. Click `Connect`
