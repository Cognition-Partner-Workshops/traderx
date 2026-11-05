package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.model.TradeSide;
import finos.traderx.tradeprocessor.model.TradeStatusHistory;
import finos.traderx.tradeprocessor.repository.TradeStatusHistoryRepository;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TradeService.class)
class TradeStatusHistoryAtomicityTest {

    private static final int ACCOUNT = 99001;

    @Autowired
    TradeService tradeService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    TradeStatusHistoryRepository tradeStatusHistoryRepository;

    @MockBean
    Publisher<Trade> tradePublisher;

    @MockBean
    Publisher<Position> positionPublisher;

    @Test
    void failedHistoryInsertRollsBackTradeStateChange() {
        when(tradeStatusHistoryRepository.save(any(TradeStatusHistory.class)))
                .thenReturn(null)
                .thenThrow(new IllegalStateException("history insert failed"));

        assertThrows(RuntimeException.class,
                () -> tradeService.processTrade(new TradeOrder("order-1", ACCOUNT, "IBM", TradeSide.Buy, 100)));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TRADES WHERE ACCOUNTID = ?", Integer.class, ACCOUNT));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM POSITIONS WHERE ACCOUNTID = ?", Integer.class, ACCOUNT));
    }
}
