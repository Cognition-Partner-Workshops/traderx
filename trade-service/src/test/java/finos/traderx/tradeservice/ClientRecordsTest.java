package finos.traderx.tradeservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.controller.TradeOrderController;
import finos.traderx.tradeservice.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fs09_orders;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/initialSchema.sql"
})
class ClientRecordsTest {
    private String previousSecret;

    @BeforeEach
    void configureTestReferenceSecret() {
        previousSecret = System.getProperty("traderx.reference.secret");
        System.setProperty("traderx.reference.secret", UUID.randomUUID().toString());
    }

    @AfterEach
    void restoreReferenceSecret() {
        if (previousSecret == null) System.clearProperty("traderx.reference.secret");
        else System.setProperty("traderx.reference.secret", previousSecret);
    }

    @MockBean Publisher<TradeOrder> tradePublisher;
    @Autowired ObjectMapper json;
    @Autowired TradeOrderController controller;

    @Test
    void publishedOrdersCarryPublicRoutingReferenceAndHttpResponseHidesLegacyIds() throws Exception {
        RestTemplate backend = mock(RestTemplate.class);
        when(backend.getForEntity(contains("/stocks/"), eq(Security.class))).thenReturn(ResponseEntity.ok(new Security("IBM", "IBM")));
        when(backend.getForEntity(contains("/account/"), eq(Account.class))).thenReturn(ResponseEntity.ok(new Account()));
        ReflectionTestUtils.setField(controller, "restTemplate", backend);
        TradeOrder order = new TradeOrder("TRADE-22214-FS09", 22214, "IBM", TradeSide.Buy, 2);
        String body = json.writeValueAsString(controller.createTradeOrder(order).getBody());
        assertFalse(body.contains("22214"), "Legacy order ids must not disclose the account number");
        JsonNode transport = new ObjectMapper().valueToTree(order);
        assertTrue(transport.path("accountReference").asText().matches("[a-f0-9-]{36}"), "Public feed orders need an opaque account reference");
        verify(tradePublisher).publish(eq("/trades"), same(order));
        TradeOrder received = new ObjectMapper().readValue("{\"accountId\":\"" + transport.path("accountReference").asText() + "\"}", TradeOrder.class);
        assertEquals(22214, received.getAccountId());
    }

    @Test
    void invalidReferencesAreRejectedWithoutEchoingAnAccountNumber() {
        assertThrows(Exception.class, () -> json.readValue("{\"accountId\":\"invalid-reference\"}", TradeOrder.class));
    }
}
