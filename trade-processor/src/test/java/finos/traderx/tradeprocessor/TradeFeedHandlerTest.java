package finos.traderx.tradeprocessor;

import finos.traderx.messaging.socketio.SocketIOEnvelope;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeFeedHandlerTest {
    @Test void dispatchesOrderAndSurvivesServiceFailure() {
        TradeService service = mock(TradeService.class);
        TradeFeedHandler handler = new TradeFeedHandler();
        ReflectionTestUtils.setField(handler, "tradeService", service);
        TradeOrder order = new TradeOrder("x", 1, "IBM", TradeSide.Buy, 4);
        when(service.processTrade(order)).thenThrow(new IllegalStateException("failed"));
        assertThatCode(() -> handler.onMessage(new SocketIOEnvelope<>("/trades", order), order)).doesNotThrowAnyException();
        verify(service).processTrade(order);
        TradeOrder next = new TradeOrder("y", 1, "IBM", TradeSide.Sell, 2);
        handler.onMessage(new SocketIOEnvelope<>("/trades", next), next);
        verify(service).processTrade(next);
    }
}
