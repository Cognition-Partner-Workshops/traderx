package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeServiceTests {
    @Mock TradeRepository tradeRepository;
    @Mock PositionRepository positionRepository;
    @Mock ApplicationEventPublisher events;
    @Spy Clock settlementClock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    @InjectMocks TradeService tradeService;

    private TradeOrder order(TradeSide side) {
        return new TradeOrder("test-order", 22214, "IBM", side, 10);
    }

    @Test
    void booksProcessingBuyAndUpdatesExistingPosition() {
        Position position = new Position();
        position.setQuantity(20);
        when(positionRepository.findByAccountIdAndSecurity(22214, "IBM")).thenReturn(position);
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Buy));
        assertEquals(TradeState.Processing, result.getTrade().getState());
        assertEquals("2026-11-09", result.getTrade().getSettlementDate().toString());
        assertEquals(30, result.getPosition().getQuantity());
        verify(tradeRepository).save(result.getTrade());
        verify(positionRepository).save(position);
        verify(events).publishEvent(result);
    }

    @Test
    void createsPositionForFirstSell() {
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Sell));
        assertEquals(TradeState.Processing, result.getTrade().getState());
        assertEquals(-10, result.getPosition().getQuantity());
        assertEquals(22214, result.getPosition().getAccountId());
        assertEquals("IBM", result.getPosition().getSecurity());
    }

    @Test
    void persistsSettlementDateWithBooking() {
        TradeBookingResult result = tradeService.processTrade(order(TradeSide.Buy));
        assertEquals("2026-11-09", result.getTrade().getSettlementDate().toString());
        assertEquals(Instant.parse("2026-11-06T12:00:00Z"), result.getTrade().getCreated().toInstant());
        verify(tradeRepository).save(result.getTrade());
    }

    @Test
    void propagatesRepositoryFailure() {
        when(positionRepository.findByAccountIdAndSecurity(22214, "IBM"))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> tradeService.processTrade(order(TradeSide.Buy)));
        verifyNoInteractions(events);
    }
}
