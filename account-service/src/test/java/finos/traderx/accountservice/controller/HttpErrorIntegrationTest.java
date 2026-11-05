package finos.traderx.accountservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import finos.traderx.accountservice.service.AccountService;
import finos.traderx.accountservice.service.AccountUserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.ControllerAdvice;

@SpringBootTest(classes = HttpErrorIntegrationTest.WebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpErrorIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import(AccountController.class)
    @ComponentScan(basePackageClasses = AccountController.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class))
    static class WebApplication {}

    @MockBean AccountService service;
    @MockBean AccountUserService userService;
    @LocalServerPort int port;

    @Test
    void D1_realHttpFailureDoesNotExposeInternalDetails() throws Exception {
        when(service.getAccountById(1)).thenThrow(new IllegalStateException("SQL internal-host"));
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/account/1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP EVIDENCE: GET /account/1 (mocked repository failure)");
            System.out.println("HTTP/1.1 " + response.statusCode());
            System.out.println("X-Correlation-ID: " + response.headers().firstValue("X-Correlation-ID").orElse("<absent>"));
            System.out.println(response.body());
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).doesNotContain("SQL", "internal-host");
            String id = response.headers().firstValue("X-Correlation-ID").orElseThrow();
            assertThat(response.body()).isEqualTo("Internal server error. Reference: " + id);
        }
    }
}
