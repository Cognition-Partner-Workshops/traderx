package finos.traderx.tradeprocessor.service;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeBookingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TradeNotifications {
    private static final Logger log = LoggerFactory.getLogger(TradeNotifications.class);
    private final Publisher<Trade> trades;
    private final Publisher<Position> positions;

    public TradeNotifications(Publisher<Trade> trades, Publisher<Position> positions) {
        this.trades = trades;
        this.positions = positions;
    }

    @TransactionalEventListener
    public void publish(TradeBookingResult result) {
        Trade trade = result.getTrade();
        try {
            trades.publish("/accounts/" + trade.getAccountId() + "/trades", trade);
        } catch (PubSubException e) {
            log.error("Failed to publish committed trade {}", trade.getId(), e);
        }
        if (result.getPosition() != null) {
            try {
                positions.publish("/accounts/" + trade.getAccountId() + "/positions", result.getPosition());
            } catch (PubSubException e) {
                log.error("Failed to publish booked position for trade {}", trade.getId(), e);
            }
        }
    }
}
