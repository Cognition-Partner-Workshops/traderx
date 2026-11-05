package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeServiceTests {
    @Mock TradeRepository tradeRepository;
    @Mock PositionRepository positionRepository;
    @Mock Publisher<Trade> tradePublisher;
    @Mock Publisher<Position> positionPublisher;
    @InjectMocks TradeService tradeService;

    private TradeOrder order(TradeSide side) {
        return new TradeOrder("test-order", 22214, "IBM", side, 10);
    }

    @Test
    void settlesBuyAndUpdatesExistingPosition() throws PubSubException {
        Position position = new Position();
        position.setQuantity(20);
        when(positionRepository.findByAccountIdAndSecurity(22214, "IBM")).thenReturn(position);
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Buy));
        assertEquals(TradeState.Settled, result.getTrade().getState());
        assertEquals(30, result.getPosition().getQuantity());
        verify(tradeRepository, times(2)).save(result.getTrade());
        verify(positionRepository).save(position);
        verify(tradePublisher).publish("/accounts/22214/trades", result.getTrade());
        verify(positionPublisher).publish("/accounts/22214/positions", position);
    }

    @Test
    void createsPositionForFirstSell() {
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Sell));
        assertEquals(TradeState.Settled, result.getTrade().getState());
        assertEquals(-10, result.getPosition().getQuantity());
        assertEquals(22214, result.getPosition().getAccountId());
        assertEquals("IBM", result.getPosition().getSecurity());
    }

    @Test
    void retainsSettledTradeWhenNotificationFails() throws PubSubException {
        doThrow(new PubSubException("feed unavailable")).when(tradePublisher).publish(anyString(), any(Trade.class));
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Buy));
        assertEquals(TradeState.Settled, result.getTrade().getState());
        verify(tradeRepository, times(2)).save(result.getTrade());
    }

    @Test
    void propagatesRepositoryFailure() {
        when(positionRepository.findByAccountIdAndSecurity(22214, "IBM"))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> tradeService.processTrade(order(TradeSide.Buy)));
        verifyNoInteractions(tradePublisher, positionPublisher);
    }
}
