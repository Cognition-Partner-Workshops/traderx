package finos.traderx.accountservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.service.AccountUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
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
            for (Object controller : new Object[] {new AccountController(), new AccountUserController()}) {
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

    @Test
    void D1_notFoundDoesNotEchoAnArbitraryExceptionMessage() {
        var error = new finos.traderx.accountservice.exceptions.ResourceNotFoundException("SQL internal-host");
        assertThat(new AccountController().resourceNotFoundExceptionMapper(error).getBody()).isEqualTo("Account not found.");
        assertThat(new AccountUserController().resourceNotFoundExceptionMapper(error).getBody()).isEqualTo("Account, account user or person not found.");
    }

    @Test
    void D1_missingAccountIsCoveredBySafeAccountUserNotFoundMessage() {
        var repository = mock(AccountRepository.class);
        when(repository.findById(123)).thenReturn(Optional.empty());
        var service = new AccountUserService();
        ReflectionTestUtils.setField(service, "accountRepository", repository);
        var accountUser = new AccountUser();
        accountUser.setAccountId(123);

        var error = assertThrows(ResourceNotFoundException.class, () -> service.upsertAccountUser(accountUser));
        var response = new AccountUserController().resourceNotFoundExceptionMapper(error);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo("Account, account user or person not found.").doesNotContain("123");
    }
}
