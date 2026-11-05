package finos.traderx.tradeprocessor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;

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

    @MockBean(name = "tradePublisher")
    private finos.traderx.messaging.Publisher<?> tradePublisher;

    @MockBean(name = "positionPublisher")
    private finos.traderx.messaging.Publisher<?> positionPublisher;

    @MockBean(name = "tradeFeedHandler")
    private finos.traderx.messaging.Subscriber<?> tradeFeedHandler;

    @Test
    void servesHttp() {
        var response = http.getForEntity("/v3/api-docs", String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful(), () -> response.toString());
    }
}
