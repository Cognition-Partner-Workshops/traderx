package finos.traderx.tradeprocessor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.model.TradeSide;

@DataJpaTest
@Import(TradeService.class)
class TradeStatusHistoryTest {

    @Autowired
    TradeService tradeService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    Publisher<Trade> tradePublisher;

    @MockBean
    Publisher<Position> positionPublisher;

    @Test
    void settledTradeKeepsOneUtcRecordPerStatusChange() {
        Instant before = Instant.now();
        Trade trade = tradeService.processTrade(new TradeOrder("order-1", 22214, "IBM", TradeSide.Buy, 100)).getTrade();
        Instant after = Instant.now();

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT STATE, CHANGEDAT FROM TRADESTATUSHISTORY WHERE TRADEID = ? ORDER BY ID", trade.getId());

        assertEquals(List.of("New", "Processing", "Settled"), history.stream().map(r -> r.get("STATE")).toList());
        for (Map<String, Object> record : history) {
            OffsetDateTime changedAt = (OffsetDateTime) record.get("CHANGEDAT");
            assertEquals(ZoneOffset.UTC, changedAt.getOffset());
            assertFalse(changedAt.toInstant().isBefore(before.minusMillis(1)));
            assertFalse(changedAt.toInstant().isAfter(after));
        }
    }
}
