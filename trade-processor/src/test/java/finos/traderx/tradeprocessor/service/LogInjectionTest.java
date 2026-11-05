package finos.traderx.tradeprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.test.util.ReflectionTestUtils;
import finos.traderx.messaging.Publisher;
import finos.traderx.messaging.PubSubException;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;

class LogInjectionTest {
    @Test
    void D3_orderAndPublishErrorsCannotForgeLogLines() throws Exception {
        var service = new TradeService();
        ReflectionTestUtils.setField(service, "tradeRepository", mock(TradeRepository.class));
        ReflectionTestUtils.setField(service, "positionRepository", mock(PositionRepository.class));
        Publisher<Trade> publisher = mock(Publisher.class);
        doThrow(new PubSubException("publish\r\nFORGED")).when(publisher).publish(anyString(), any());
        ReflectionTestUtils.setField(service, "tradePublisher", publisher);
        ReflectionTestUtils.setField(service, "positionPublisher", mock(Publisher.class));
        Logger logger = (Logger) LoggerFactory.getLogger(TradeService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            service.processTrade(new TradeOrder("id", 22214, "IBM\r\nFORGED", TradeSide.Buy, 10));
            assertThat(events.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("\r", "\n");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(events.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("IBM__FORGED"));
            assertThat(events.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("publish__FORGED"));
        } finally {
            logger.detachAppender(events);
        }
    }
}
