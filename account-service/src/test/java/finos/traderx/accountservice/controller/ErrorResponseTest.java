package finos.traderx.accountservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.stream.Stream;

import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.service.AccountService;
import finos.traderx.accountservice.service.AccountUserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ErrorResponseTest {

    private static final String SENSITIVE = "SELECT * FROM accounts WHERE secret='password' org.h2.jdbc.JdbcSQLException";

    static Stream<Arguments> unexpectedErrors() {
        return Stream.of(false, true).flatMap(accountUser -> Stream.of(
                Arguments.of(accountUser, new RuntimeException(SENSITIVE)),
                Arguments.of(accountUser, new DataAccessResourceFailureException(SENSITIVE, new SQLException(SENSITIVE))),
                Arguments.of(accountUser, new SQLException(SENSITIVE)),
                Arguments.of(accountUser, new RuntimeException())));
    }

    private MockMvc mvc(boolean accountUser, Exception error) {
        if (accountUser) {
            AccountUserController controller = new AccountUserController();
            controller.accountUserService = mock(AccountUserService.class);
            when(controller.accountUserService.getAccountUserById(1)).thenAnswer(invocation -> { throw error; });
            return MockMvcBuilders.standaloneSetup(controller).build();
        }
        AccountController controller = new AccountController();
        controller.accountService = mock(AccountService.class);
        when(controller.accountService.getAccountById(1)).thenAnswer(invocation -> { throw error; });
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @MethodSource("unexpectedErrors")
    void unexpectedErrorsDoNotExposeInternals(boolean accountUser, Exception error) throws Exception {
        String body = mvc(accountUser, error).perform(get(accountUser ? "/accountuser/1" : "/account/1"))
                .andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT", "password", "org.h2", "SQLException", "RuntimeException", "at finos.");
        assertThat(body).isEqualTo("An unexpected error occurred.");
    }

    static Stream<Boolean> controllers() {
        return Stream.of(false, true);
    }

    @ParameterizedTest
    @MethodSource("controllers")
    void notFoundKeepsIntentionalMessage(boolean accountUser) throws Exception {
        String body = mvc(accountUser, new ResourceNotFoundException("Account not found"))
                .perform(get(accountUser ? "/accountuser/1" : "/account/1"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(body).isEqualTo("Account not found");
    }
}
