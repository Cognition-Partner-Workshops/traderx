package finos.traderx.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApplicationSmokeTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void servesHttp() {
        var response = http.getForEntity("/account/", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(), () -> response.toString());
    }
}
