package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeBookingResult;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.model.TradeSide;
import finos.traderx.tradeprocessor.model.TradeState;
import finos.traderx.tradeprocessor.model.TradeStateChange;
import finos.traderx.tradeprocessor.repository.PositionRepository;
import finos.traderx.tradeprocessor.repository.TradeRepository;
import finos.traderx.tradeprocessor.repository.TradeStateChangeRepository;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

	private static final Instant BOOKED_AT = Instant.parse("2027-03-01T08:15:30Z");

	@Mock TradeRepository tradeRepository;
	@Mock PositionRepository positionRepository;
	@Mock TradeStateChangeRepository tradeStateChangeRepository;
	@Mock Publisher<Trade> tradePublisher;
	@Mock Publisher<Position> positionPublisher;

	@InjectMocks TradeService service;

	@Test
	void everyStateChangeIsKeptAsItsOwnUtcRecord() {
		service.setClock(Clock.fixed(BOOKED_AT, ZoneOffset.ofHours(-5)));
		when(positionRepository.findByAccountIdAndSecurity(22214, "IBM")).thenReturn(null);

		TradeBookingResult result = service.processTrade(new TradeOrder("order-1", 22214, "IBM", TradeSide.Buy, 100));

		ArgumentCaptor<TradeStateChange> changes = ArgumentCaptor.forClass(TradeStateChange.class);
		verify(tradeStateChangeRepository, org.mockito.Mockito.times(3)).save(changes.capture());
		List<TradeStateChange> history = changes.getAllValues();
		assertEquals(List.of(TradeState.New, TradeState.Processing, TradeState.Settled),
				history.stream().map(TradeStateChange::getState).toList());
		for (TradeStateChange change : history) {
			assertEquals(result.getTrade().getId(), change.getTradeId());
			assertEquals(BOOKED_AT, change.getChangedAt(), "state change is stamped in UTC regardless of the host zone");
		}
		assertEquals(TradeState.Settled, result.getTrade().getState());
		assertEquals(BOOKED_AT, result.getTrade().getUpdated().toInstant());
	}

	@Test
	void tradeRecordHoldsAccountInstrumentSideAndQuantity() {
		service.setClock(Clock.fixed(BOOKED_AT, ZoneOffset.UTC));
		when(positionRepository.findByAccountIdAndSecurity(52355, "BAC")).thenReturn(null);

		Trade t = service.processTrade(new TradeOrder("order-2", 52355, "BAC", TradeSide.Sell, 2400)).getTrade();

		assertNotNull(t.getId());
		assertEquals(52355, t.getAccountId());
		assertEquals("BAC", t.getSecurity());
		assertEquals(TradeSide.Sell, t.getSide());
		assertEquals(2400, t.getQuantity());
	}
}
