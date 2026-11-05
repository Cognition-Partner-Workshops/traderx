# Deploy TraderX Using Radius

Radius can be used to deploy and run the [TraderX](https://github.com/finos/traderX) application.

## Prerequisites

- Install and set up [Radius](https://docs.radapp.io/getting-started/) and local Kubernetes cluster
- Optionally set up an [Azure](https://docs.radapp.io/providers/azure/) or [AWS](https://docs.radapp.io/providers/aws/) provider and environment in Radius

## Instructions

To deploy the TraderX app using Radius, you can run the commands directly in this directory.

First, initialize Radius:
```bash
rad init
```

>Select `Yes` when prompted to setup an application in the current directory

Set `TRADE_FEED_PUBLISH_TOKEN` in your shell to a random shared secret (never commit it).
Pass it as the secure `tradeFeedPublishToken` parameter; the app supplies the same value to trade-feed, trade-service and trade-processor.

To deploy and run the app in your local environment:
```bash
rad run app.bicep -p tradeFeedPublishToken="$TRADE_FEED_PUBLISH_TOKEN"
```

If you have an Azure environment set up, you can deploy the app there as well (in this example the Azure environment is associated with the `prod-azure` workspace):
```bash
rad deploy app.bicep -w prod-azure -p tradeFeedPublishToken="$TRADE_FEED_PUBLISH_TOKEN"
```

If you have an AWS environment set up, you can deploy the app there as well (in this example the AWS environment is associated with the `prod-aws` workspace):
```bash
rad deploy app.bicep -w prod-aws -p tradeFeedPublishToken="$TRADE_FEED_PUBLISH_TOKEN"
```