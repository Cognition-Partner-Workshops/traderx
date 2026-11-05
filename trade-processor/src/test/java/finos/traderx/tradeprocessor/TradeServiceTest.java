package finos.traderx.tradeprocessor;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import finos.traderx.tradeprocessor.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeServiceTest {
    TradeRepository trades = mock(TradeRepository.class);
    PositionRepository positions = mock(PositionRepository.class);
    Publisher<Trade> tradePublisher = mock(Publisher.class);
    Publisher<Position> positionPublisher = mock(Publisher.class);
    TradeService service = new TradeService();
    List<TradeState> savedStates = new ArrayList<>();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "tradeRepository", trades);
        ReflectionTestUtils.setField(service, "positionRepository", positions);
        ReflectionTestUtils.setField(service, "tradePublisher", tradePublisher);
        ReflectionTestUtils.setField(service, "positionPublisher", positionPublisher);
        when(trades.save(any())).thenAnswer(inv -> {
            Trade trade = inv.getArgument(0);
            savedStates.add(trade.getState());
            return trade;
        });
    }

    @ParameterizedTest @EnumSource(TradeSide.class)
    void createsPositionAndSettlesTrade(TradeSide side) throws Exception {
        TradeBookingResult result = service.processTrade(new TradeOrder("order-1", 7, "IBM", side, 12));
        assertThat(result.getPosition().getQuantity()).isEqualTo(side == TradeSide.Buy ? 12 : -12);
        assertThat(result.getPosition().getAccountId()).isEqualTo(7);
        assertThat(result.getPosition().getSecurity()).isEqualTo("IBM");
        assertThat(savedStates).containsExactly(TradeState.New, TradeState.Settled);
        assertThat(result.getTrade().getState()).isEqualTo(TradeState.Settled);
        assertThat(result.getTrade().getId()).isNotEqualTo("order-1").matches("[0-9a-f-]{36}");
        assertThat(result.getTrade().getAccountId()).isEqualTo(7);
        assertThat(result.getTrade().getSecurity()).isEqualTo("IBM");
        assertThat(result.getTrade().getSide()).isEqualTo(side);
        assertThat(result.getTrade().getQuantity()).isEqualTo(12);
        assertThat(result.getTrade().getUpdated()).isAfterOrEqualTo(result.getTrade().getCreated());
        verify(positions).save(result.getPosition());
        verify(tradePublisher).publish("/accounts/7/trades", result.getTrade());
        verify(positionPublisher).publish("/accounts/7/positions", result.getPosition());
    }

    @ParameterizedTest @EnumSource(TradeSide.class)
    void updatesExistingPosition(TradeSide side) {
        Position position = new Position();
        position.setQuantity(20);
        when(positions.findByAccountIdAndSecurity(7, "IBM")).thenReturn(position);
        TradeBookingResult result = service.processTrade(new TradeOrder("x", 7, "IBM", side, 5));
        assertThat(result.getPosition()).isSameAs(position);
        assertThat(position.getQuantity()).isEqualTo(side == TradeSide.Buy ? 25 : 15);
        verify(positions).save(position);
    }

    @Test void publishingFailureDoesNotUndoPersistence() throws Exception {
        doThrow(new PubSubException("offline")).when(tradePublisher).publish(anyString(), any());
        TradeBookingResult result = service.processTrade(new TradeOrder("x", 7, "IBM", TradeSide.Buy, 0));
        assertThat(result.getPosition().getQuantity()).isZero();
        assertThat(savedStates).containsExactly(TradeState.New, TradeState.Settled);
        verify(positions).save(result.getPosition());
        verifyNoInteractions(positionPublisher);
    }

    @Test void persistenceFailurePropagatesWithoutPublishing() {
        doThrow(new IllegalStateException("database unavailable")).when(trades).save(any());
        assertThatThrownBy(() -> service.processTrade(new TradeOrder("x", 7, "IBM", TradeSide.Buy, 2)))
            .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        verify(positions, never()).save(any());
        verifyNoInteractions(tradePublisher, positionPublisher);
    }
}
