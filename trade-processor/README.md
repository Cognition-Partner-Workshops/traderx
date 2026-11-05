# FINOS | TraderX Sample Trading App | Trade Processor

![DEV Only Warning](https://badgen.net/badge/warning/not-for-production/red) ![Local Dev Machine Supported](http://badgen.net/badge/local-dev/supported/green)

# trade-processor

A simple application that subscribes to the trade-feed pubsub engine and books trades in `Processing` state. Booking atomically stores the trade and updates positions, then broadcasts both after commit.

## Equity settlement

`SettlementCalendar` assigns the next Monday–Friday business day after the UTC booking date. Friday, Saturday and Sunday bookings all settle Monday; holidays are not modelled. The date is stored as SQL `DATE` and sent as an ISO `settlementDate` string (`YYYY-MM-DD`) in history and live trade messages.

The scheduler checks persisted `Processing` trades every second (`settlement.poll-interval-ms` overrides the interval). On or after the stored date, it locks due trades, marks them `Settled`, and publishes a trade-only update after commit. It catches up after downtime and does not move positions again. `Settled` and `Cancelled` trades are excluded. Production time comes from an injected `Clock.systemUTC()`; tests inject a controlled clock rather than changing trade dates or states.

The database runner recreates the sample schema on startup, as before. Its seeded settled trades now have explicit historical booking and settlement dates. This sample does not introduce a production database migration framework or durable message delivery; feed publication failures are logged and history remains authoritative.
 
# How to run the application (WIP)

- Check out the source code from git
- change any ports needed
- ``gradlew bootRun``

# Configuration

The easiest way to reconfigure the application is by editing properties in:

    `src/main/resources/application.properties`

Alternatively you can use environment variables to override certain values:

   `export TRADE_PROCESSOR_SERVICE_PORT=18091`

Or you can use command line arguments:
    
   $ gradlew bootRun --args='--server.port=18091'
   
The app by default runs on port 18091

## Trade Feed Location

You can either specify `TRADE_FEED_ADDRESS` as an environment variable (should be a URL in the current SocketIO implementation) or specify the app property per below

To specify the host and the port for the TradeFeed instance to subscribe to use the `trade.feed.address` property using one of the ways describe above, like

   $ gradlew bootRun --args='--trade.feed.address=http://localhost:18086

## Database settings

To specify the database properties, the following properties should be used

    spring.datasource.url=jdbc:h2:tcp://localhost:18082/traderx
    spring.datasource.username=sa
    spring.datasource.password=sa


You can see all configuration details in [src/main/resources/application.properties](application.properties)