package finos.traderx.tradeprocessor.service;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class TradeNotificationsTests {
    @SuppressWarnings("unchecked")
    private final Publisher<Trade> trades = mock(Publisher.class);
    @SuppressWarnings("unchecked")
    private final Publisher<Position> positions = mock(Publisher.class);
    private final TradeNotifications notifications = new TradeNotifications(trades, positions);

    private Trade trade() {
        Trade trade = new Trade();
        trade.setId("committed-trade");
        trade.setAccountId(22214);
        return trade;
    }

    @Test
    void tradeFeedFailureDoesNotSuppressBookingPositionNotification() throws Exception {
        Trade trade = trade();
        Position position = new Position();
        doThrow(new PubSubException("feed unavailable")).when(trades).publish(anyString(), any(Trade.class));
        assertDoesNotThrow(() -> notifications.publish(new TradeBookingResult(trade, position)));
        verify(positions).publish("/accounts/22214/positions", position);
    }

    @Test
    void settlementPublishesOnlyTheTrade() throws Exception {
        Trade trade = trade();
        notifications.publish(new TradeBookingResult(trade, null));
        verify(trades).publish("/accounts/22214/trades", trade);
        verifyNoInteractions(positions);
    }

    @Test
    void positionFeedFailureDoesNotFailCommittedBooking() throws Exception {
        doThrow(new PubSubException("feed unavailable")).when(positions).publish(anyString(), any(Position.class));
        assertDoesNotThrow(() -> notifications.publish(new TradeBookingResult(trade(), new Position())));
    }
}
