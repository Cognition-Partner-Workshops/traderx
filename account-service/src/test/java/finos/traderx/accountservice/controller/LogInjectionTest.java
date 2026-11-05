package finos.traderx.accountservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import finos.traderx.accountservice.model.Person;

class LogInjectionTest {
    @Test
    void D3_userValuesAndDownstreamErrorsCannotForgeLogLines() {
        var controller = new AccountUserController();
        RestTemplate client = mock(RestTemplate.class);
        ReflectionTestUtils.setField(controller, "restTemplate", client);
        Logger logger = (Logger) LoggerFactory.getLogger(AccountUserController.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            for (HttpStatus status : new HttpStatus[] {HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST}) {
                when(client.getForEntity(anyString(), eq(Person.class)))
                    .thenThrow(HttpClientErrorException.create(status, "downstream\r\nFORGED", null, null, null));
                assertThat((Boolean) ReflectionTestUtils.invokeMethod(controller, "validatePerson", "alice\r\nFORGED")).isFalse();
            }
            assertThat(events.list).hasSize(2);
            assertThat(events.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("FORGED").doesNotContain("\r", "\n");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(events);
        }
    }
}
