package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TradeService.class)
class TradePublicationTest {
    @Autowired TradeService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate database;
    @MockBean Publisher<Trade> tradePublisher;
    @MockBean Publisher<Position> positionPublisher;

    @Test
    void rolledBackBookingDoesNotPublish() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.processTrade(order(99011));
            status.setRollbackOnly();
        });
        assertNoBooking(99011);
        verifyNoInteractions(tradePublisher, positionPublisher);
    }

    @Test
    void failedCommitDoesNotPublish() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.processTrade(order(99012));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("fs09 commit failure");
                }
            });
        }));
        assertNoBooking(99012);
        verifyNoInteractions(tradePublisher, positionPublisher);
    }

    @Test
    void successfulCommitPublishesBothUpdatesOnlyAfterCommit() throws Exception {
        TradeBookingResult result = new TransactionTemplate(transactions).execute(status -> {
            TradeBookingResult booking = service.processTrade(order(99013));
            verifyNoInteractions(tradePublisher, positionPublisher);
            return booking;
        });
        assertEquals(1, database.queryForObject("SELECT COUNT(*) FROM TRADES WHERE ACCOUNTID = 99013", Integer.class));
        verify(tradePublisher).publish("/accounts/99013/trades", result.getTrade());
        verify(positionPublisher).publish("/accounts/99013/positions", result.getPosition());
        verifyNoMoreInteractions(tradePublisher, positionPublisher);
    }

    private TradeOrder order(int account) {
        return new TradeOrder("fs09", account, "IBM", TradeSide.Buy, 17);
    }

    private void assertNoBooking(int account) {
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM TRADES WHERE ACCOUNTID = ?", Integer.class, account));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM POSITIONS WHERE ACCOUNTID = ?", Integer.class, account));
    }
}
