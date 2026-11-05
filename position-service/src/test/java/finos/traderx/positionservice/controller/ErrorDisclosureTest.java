package finos.traderx.positionservice.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ErrorDisclosureTest {
    @Test
    void D1_serverErrorsAreGenericAndCorrelatedWithSingleLineServerDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            for (Object controller : new Object[] {new PositionController(), new TradeController()}) {
                ResponseEntity<String> response;
                try {
                    response = (ResponseEntity<String>) controller.getClass()
                        .getMethod("generalError", Exception.class)
                        .invoke(controller, new IllegalStateException("SQL internal-host\r\nFORGED"));
                } catch (ReflectiveOperationException ex) {
                    throw new AssertionError(ex);
                }
                System.out.println("D1 HTTP " + response.getStatusCode().value() + " " + response.getHeaders() + " " + response.getBody());
                assertThat(response.getStatusCode().value()).isEqualTo(500);
                assertThat(response.getBody()).doesNotContain("SQL", "internal-host", "FORGED");
                String id = response.getHeaders().getFirst("X-Correlation-ID");
                assertThat(id).isNotNull();
                assertThat(UUID.fromString(id).toString()).isEqualTo(id);
                assertThat(response.getBody()).isEqualTo("Internal server error. Reference: " + id);
                assertThat(events.list).anySatisfy(event -> {
                    assertThat(event.getFormattedMessage()).contains(id, "SQL internal-host", "FORGED");
                    assertThat(event.getFormattedMessage()).doesNotContain("\r", "\n");
                });
            }
        } finally {
            logger.detachAppender(events);
        }
    }

}
