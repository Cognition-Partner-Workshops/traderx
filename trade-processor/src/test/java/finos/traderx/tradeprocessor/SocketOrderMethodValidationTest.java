package finos.traderx.tradeprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import finos.traderx.tradeprocessor.service.TradeService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {TradeService.class, ValidationAutoConfiguration.class})
class SocketOrderMethodValidationTest {
    @Autowired TradeService service;
    @MockBean TradeRepository trades;
    @MockBean PositionRepository positions;
    @MockBean Publisher<Trade> tradePublisher;
    @MockBean Publisher<Position> positionPublisher;

    @Test
    void negativeBuyCannotReducePosition() {
        assertThrows(ConstraintViolationException.class,
            () -> service.processTrade(new TradeOrder("id", 22, "IBM", TradeSide.Buy, -10)));
        verifyNoInteractions(trades, positions, tradePublisher, positionPublisher);
    }

    @Test
    void nullQuantityCannotReachPersistence() throws Exception {
        TradeOrder order = new ObjectMapper().readValue(
            "{\"accountId\":22,\"security\":\"IBM\",\"side\":\"Buy\"}", TradeOrder.class);
        assertThrows(ConstraintViolationException.class, () -> service.processTrade(order));
        verifyNoInteractions(trades, positions, tradePublisher, positionPublisher);
    }

    @Test
    void validBuyIncreasesExistingPosition() {
        Position position = new Position();
        position.setAccountId(22);
        position.setSecurity("IBM");
        position.setQuantity(15);
        when(positions.findByAccountIdAndSecurity(22, "IBM")).thenReturn(position);
        service.processTrade(new TradeOrder("id", 22, "IBM", TradeSide.Buy, 10));
        assertEquals(25, position.getQuantity());
        verify(positions).save(position);
        verify(trades, times(2)).save(any(Trade.class));
    }
}
