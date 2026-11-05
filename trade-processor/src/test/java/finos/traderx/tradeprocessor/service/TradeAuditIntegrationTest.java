package finos.traderx.tradeprocessor.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.Position;
import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.model.TradeSide;
import finos.traderx.tradeprocessor.repository.PositionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:FSX_20261105_FS14;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.test.database.replace=none",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/initialSchema.sql"
})
@Import(TradeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradeAuditIntegrationTest {
    @Autowired private TradeService service;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private PositionRepository positions;
    @MockBean(name = "tradePublisher") private Publisher<Trade> tradePublisher;
    @MockBean(name = "positionPublisher") private Publisher<Position> positionPublisher;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM TradeAudit");
        jdbc.update("DELETE FROM Trades WHERE AccountID = 44044");
        jdbc.update("DELETE FROM Positions WHERE AccountID = 44044");
    }

    @Test
    void booksOneDurableAuditWithUtcTimestamp() {
        Instant before = Instant.now();
        var result = service.processTrade(new TradeOrder("request", 44044, "MS", TradeSide.Sell, 42));
        Instant after = Instant.now();

        var rows = jdbc.queryForList("SELECT * FROM TradeAudit");
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("ACTOR")).isEqualTo("trade-processor");
        assertThat(row.get("ACTION")).isEqualTo("TRADE_BOOKED");
        assertThat(row.get("ACCOUNTID")).isEqualTo(44044);
        assertThat(row.get("SECURITY")).isEqualTo("MS");
        assertThat(row.get("SIDE")).isEqualTo("Sell");
        assertThat(row.get("QUANTITY")).isEqualTo(42);
        assertThat(row.get("TRADEID")).isEqualTo(result.getTrade().getId());
        OffsetDateTime created = (OffsetDateTime) row.get("CREATEDUTC");
        assertThat(created.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(created.toInstant()).isBetween(before, after);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Trades WHERE AccountID = 44044", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT Quantity FROM Positions WHERE AccountID = 44044 AND Security = 'MS'", Integer.class)).isEqualTo(-42);
    }

    @Test
    void rollsBackTradeAndAuditWhenPositionSaveFails() {
        doAnswer(invocation -> {
            positions.flush();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Trades WHERE AccountID = 44044", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TradeAudit", Integer.class)).isEqualTo(1);
            throw new IllegalStateException("FSX_20261105_FS14 position save failure");
        }).when(positions).save(any(Position.class));

        assertThatThrownBy(() -> service.processTrade(new TradeOrder("request", 44044, "MS", TradeSide.Buy, 42)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("FSX_20261105_FS14 position save failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TradeAudit", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Trades WHERE AccountID = 44044", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Positions WHERE AccountID = 44044", Integer.class)).isZero();
    }
}
