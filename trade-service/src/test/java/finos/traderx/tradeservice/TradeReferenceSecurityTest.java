package finos.traderx.tradeservice;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import finos.traderx.tradeservice.model.TradeOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeReferenceSecurityTest {
    private static final String PROPERTY = "traderx.reference.secret";
    private static final String LEGACY = "TRADE-22214-AABBCC";

    private JsonNode encoded(String id) throws Exception {
        TradeOrder order = new TradeOrder();
        order.id = id;
        ObjectMapper json = new ObjectMapper();
        return json.readTree(json.writeValueAsString(order)).get("id");
    }

    @AfterEach
    void clearSecret() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void legacyTradeIdsCannotBeMatchedByOfflineAccountCandidates() throws Exception {
        System.setProperty(PROPERTY, UUID.randomUUID().toString());
        String reference = encoded(LEGACY).asText();
        assertNotEquals(UUID.nameUUIDFromBytes(LEGACY.getBytes(StandardCharsets.UTF_8)).toString(), reference,
            "A known legacy suffix must not permit an offline account-number dictionary");
        assertTrue(reference.matches("[a-f0-9]{64}"));
        assertEquals(reference, encoded(LEGACY).asText());
        System.setProperty(PROPERTY, UUID.randomUUID().toString());
        assertNotEquals(reference, encoded(LEGACY).asText());
    }

    @Test
    void legacyIdsFailClosedWithoutASecretButRandomAndEmptyIdsRemainUnchanged() throws Exception {
        for (String secret : new String[] {"", "short"}) {
            System.setProperty(PROPERTY, secret);
            JsonMappingException error = assertThrows(JsonMappingException.class, () -> encoded(LEGACY));
            assertTrue(error.getMessage().contains("TRADERX_REFERENCE_SECRET"));
            assertFalse(error.getMessage().contains("22214"));
        }
        String random = UUID.randomUUID().toString();
        assertEquals(random, encoded(random).asText());
        assertEquals("", encoded("").asText());
        assertTrue(encoded(null).isNull());
    }
}
