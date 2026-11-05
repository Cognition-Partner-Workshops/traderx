---
name: traderx-local-stack
description: Build and run TraderX locally with Docker Compose, book and verify a trade, run service tests, and reset the database.
---

# TraderX local stack

Run commands from the repository root with Docker Engine and Docker Compose installed. Host Java tests require JDK 21. Set JAVA_HOME explicitly if the shell selects an older JDK.

## Start

```sh
docker compose -p fsx_20261105_traderx up -d --build
docker compose -p fsx_20261105_traderx ps
docker compose -p fsx_20261105_traderx logs --tail 50
```

Open http://localhost:8080/trade to view the trades and positions blotter. The production frontend configuration routes API requests through ingress on port 8080. Port 18093 serves the frontend directly, but API requests still need ingress when using the production configuration.

Compose reports containers started before Spring Boot and Angular finish loading. Wait for HTTP 200 from http://localhost:18092/v3/api-docs and http://localhost:8080/ before booking trades. The trade processor also needs to finish subscribing to the trade feed; check http://localhost:18091/v3/api-docs and the processor logs.

Gradle resolves Maven Central artifacts through Google's public Maven Central mirror, including plugin dependencies. The mirror avoids persistent HTTP 429 responses observed from repo.maven.apache.org and repo1.maven.org. The Gradle Plugin Portal supplies plugin markers absent from Central.

## Ports

| Service | Local port |
| --- | --- |
| ingress (browser entry point) | 8080 |
| database H2 TCP | 18082 |
| database PostgreSQL protocol | 18083 |
| database web console | 18084 |
| reference-data | 18085 |
| trade-feed (Socket.IO) | 18086 |
| account-service | 18088 |
| people-service | 18089 |
| position-service | 18090 |
| trade-processor | 18091 |
| trade-service | 18092 |
| web-front-end-angular | 18093 |

## Book a trade

The database seeds account 22214, and reference-data includes IBM.

```sh
curl --fail-with-body -i http://localhost:18092/trade/ \
  -H 'Content-Type: application/json' \
  -d '{"id":"local-check-001","accountId":22214,"security":"IBM","side":"Buy","quantity":10}'
curl --fail-with-body http://localhost:18090/trades/22214
```

POST returns HTTP 200 with the submitted order. Processing is asynchronous, so poll the trades endpoint until the new IBM Buy for quantity 10 has state Settled. The processor assigns a new UUID instead of preserving the submitted order ID. Compare the trades before and after booking to distinguish the new trade from seeded rows. Repeating POST books another trade.

## Tests

Run the Java unit tests with the root Gradle wrapper:

```sh
./gradlew :trade-processor:test :position-service:test
./gradlew :account-service:test
./gradlew :trade-service:test
./gradlew :database:test
```

Processor and position tests use Mockito and run independently of the stack. Reports appear under each service's build/reports/tests/test directory. Account-service and trade-service still contain legacy tests under src/main/test; the default Gradle test task does not discover those files. Database currently has no Java tests.

Run Node tests inside the installed service containers:

```sh
docker compose -p fsx_20261105_traderx exec -T reference-data npm test -- --runInBand
docker compose -p fsx_20261105_traderx exec -T reference-data npm run test:e2e -- --runInBand
docker compose -p fsx_20261105_traderx exec -T web-front-end-angular npm run test:ci
docker compose -p fsx_20261105_traderx exec -T trade-feed npm test
```

Angular's Karma tests require a Chrome installation compatible with Puppeteer. The frontend image skips Puppeteer's browser download, so install the browser before running Karma tests. The Angular suite also has a preexisting stale getRowNodeId reference in trade-blotter.component.spec.ts. Trade-feed's test script currently exits with "no test specified".

Run the .NET test command inside people-service:

```sh
docker compose -p fsx_20261105_traderx exec -T people-service dotnet test PeopleService.sln
```

The solution currently contains application projects only; dotnet test does not provide unit-test coverage until a test project is added.

## Reset

```sh
docker compose -p fsx_20261105_traderx down -v
docker compose -p fsx_20261105_traderx up -d --build
```

Reset removes the containers and volumes, and database startup recreates the seeded schema. Local trades and position changes are discarded. Retained Docker image layers make subsequent builds faster. After rebuilding an individual backend or frontend while keeping ingress running, restart ingress to refresh nginx's upstream container addresses:

```sh
docker compose -p fsx_20261105_traderx restart ingress
```
