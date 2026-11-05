package finos.traderx.tradeprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import finos.traderx.messaging.Publisher;
import finos.traderx.messaging.Subscriber;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fs09_processor;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/initialSchema.sql"
})
class ClientRecordsTest {
    @MockBean Publisher<Trade> tradePublisher;
    @MockBean Publisher<Position> positionPublisher;
    @MockBean Subscriber<TradeOrder> tradeFeedHandler;
    @Autowired TradeService service;
    @Autowired JdbcTemplate database;
    @Autowired ObjectMapper json;

    @Test
    void bookingPersistsEveryTransitionAndPublishesOnlyReferencesAndMaskedNumbers() throws Exception {
        TradeBookingResult booking = service.processTrade(new TradeOrder("fs09", 22214, "IBM", TradeSide.Buy, 2));
        assertEquals(List.of("New", "Processing", "Settled"), database.queryForList(
                "SELECT State FROM TradeStatusHistory WHERE TradeID = ? ORDER BY ID", String.class, booking.getTrade().getId()));
        String reference = database.queryForObject("SELECT PublicReference FROM Accounts WHERE ID=22214", String.class);
        verify(tradePublisher).publish(eq("/accounts/" + reference + "/trades"), any(Trade.class));
        verify(positionPublisher).publish(eq("/accounts/" + reference + "/positions"), any(Position.class));
        String body = json.writeValueAsString(booking);
        assertFalse(body.contains("22214"));
        assertTrue(body.contains("****2214"));
        TradeOrder decoded = json.readValue("{\"accountId\":\"" + reference + "\",\"security\":\"IBM\",\"side\":\"Buy\",\"quantity\":2}", TradeOrder.class);
        assertEquals(22214, decoded.getAccountId());
        ObjectMapper socketMapper = new ObjectMapper();
        assertEquals(22214, socketMapper.readValue(socketMapper.writeValueAsString(decoded), TradeOrder.class).getAccountId());
    }

    @Test
    void failedBookingRollsBackTradeAndHistory() {
        assertThrows(RuntimeException.class, () -> service.processTrade(new TradeOrder("fs09", 22214, "IBM", TradeSide.Buy, -1)));
        assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM Trades WHERE Quantity < 0", Integer.class));
    }
}
