package finos.traderx.messaging.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.socket.client.Socket;

class PublisherLogInjectionTest {
    @Test
    void D3_publishFailuresUseSanitisedLoggerInsteadOfRawStderr() throws Exception {
        var publisher = new SocketIOJSONPublisher<String>() {};
        publisher.connected = true;
        publisher.socket = mock(Socket.class);
        doThrow(new IllegalStateException("transport\r\nFORGED")).when(publisher.socket).emit(eq("publish"), any());
        Logger logger = (Logger) LoggerFactory.getLogger(publisher.getClass());
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        var stderr = new ByteArrayOutputStream();
        PrintStream original = System.err;
        try (var captured = new PrintStream(stderr)) {
            System.setErr(captured);
            publisher.publish("/trades", "payload");
            assertThat(stderr.toString()).isEmpty();
            assertThat(events.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("transport__FORGED").doesNotContain("\r", "\n");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            System.setErr(original);
            logger.detachAppender(events);
        }
    }
}
