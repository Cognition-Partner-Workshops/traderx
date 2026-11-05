package finos.traderx.tradeprocessor.service;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({TradeService.class, TradeNotifications.class})
class SettlementPersistenceTests {
    @Autowired TradeService service;
    @Autowired TradeRepository trades;
    @Autowired PositionRepository positions;
    @Autowired EntityManager em;
    @MockBean Clock settlementClock;
    @MockBean Publisher<Trade> tradePublisher;
    @MockBean Publisher<Position> positionPublisher;

    private void at(String instant) {
        when(settlementClock.instant()).thenReturn(Instant.parse(instant));
        when(settlementClock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private Trade book() {
        return service.processTrade(new TradeOrder("order", 22214, "IBM", TradeSide.Buy, 10)).getTrade();
    }

    @ParameterizedTest
    @CsvSource({"2026-11-02,2026-11-03", "2026-11-03,2026-11-04", "2026-11-04,2026-11-05",
        "2026-11-05,2026-11-06", "2026-11-06,2026-11-09", "2026-11-07,2026-11-09", "2026-11-08,2026-11-09"})
    void storesDateAndKeepsProcessingUntilDue(String booked, String due) {
        at(booked + "T12:00:00Z");
        String id = book().getId();
        em.flush();
        em.clear();
        assertEquals(LocalDate.parse(due), trades.findById(id).orElseThrow().getSettlementDate());
        at(due + "T00:00:00Z");
        when(settlementClock.instant()).thenReturn(LocalDate.parse(due).atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(1));
        service.settleDueTrades();
        assertEquals(TradeState.Processing, trades.findById(id).orElseThrow().getState());
        at(due + "T00:00:00Z");
        service.settleDueTrades();
        em.flush();
        em.clear();
        assertEquals(TradeState.Settled, trades.findById(id).orElseThrow().getState());
        assertEquals(10, positions.findByAccountIdAndSecurity(22214, "IBM").getQuantity());
        service.settleDueTrades();
        assertEquals(10, positions.findByAccountIdAndSecurity(22214, "IBM").getQuantity());
    }

    @Test
    void catchesUpOverdueButIgnoresCancelledAndAlreadySettled() {
        at("2026-11-06T12:00:00Z");
        Trade overdue = book();
        Trade cancelled = book();
        cancelled.setState(TradeState.Cancelled);
        trades.save(cancelled);
        Trade settled = book();
        settled.setState(TradeState.Settled);
        trades.save(settled);
        at("2026-11-10T08:00:00Z");
        service.settleDueTrades();
        assertEquals(TradeState.Settled, trades.findById(overdue.getId()).orElseThrow().getState());
        assertEquals(TradeState.Cancelled, trades.findById(cancelled.getId()).orElseThrow().getState());
        assertEquals(TradeState.Settled, trades.findById(settled.getId()).orElseThrow().getState());
        assertEquals(30, positions.findByAccountIdAndSecurity(22214, "IBM").getQuantity());
    }

    @Test
    void publishesOnlyAfterCommitAndSettlementDoesNotRepublishPositions() throws Exception {
        at("2026-11-06T12:00:00Z");
        Trade trade = book();
        verifyNoInteractions(tradePublisher, positionPublisher);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(tradePublisher).publish("/accounts/22214/trades", trade);
        verify(positionPublisher).publish(eq("/accounts/22214/positions"), any(Position.class));
        clearInvocations(tradePublisher, positionPublisher);
        at("2026-11-09T00:00:00Z");
        service.settleDueTrades();
        verify(tradePublisher).publish(eq("/accounts/22214/trades"), argThat(t -> t.getState() == TradeState.Settled
            && t.getSettlementDate().equals(LocalDate.parse("2026-11-09"))));
        verifyNoInteractions(positionPublisher);
        service.settleDueTrades();
        verifyNoMoreInteractions(tradePublisher);
        trades.deleteAll();
        positions.deleteAll();
    }

    @Test
    void rollbackDoesNotPublishOrMovePositions() {
        at("2026-11-06T12:00:00Z");
        book();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(tradePublisher, positionPublisher);
        assertEquals(0, trades.count());
        assertEquals(0, positions.count());
    }
}
