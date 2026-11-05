package finos.traderx.tradeprocessor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FeedPayloadMaskingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tradePayloadShowsOnlyLastFourDigitsOfAccount() throws Exception {
        Trade trade = new Trade();
        trade.setAccountId(22214);
        JsonNode json = objectMapper.valueToTree(trade);
        assertEquals("****2214", json.get("accountId").asText());
    }

    @Test
    void positionPayloadShowsOnlyLastFourDigitsOfAccount() throws Exception {
        Position position = new Position();
        position.setAccountId(22214);
        JsonNode json = objectMapper.valueToTree(position);
        assertEquals("****2214", json.get("accountId").asText());
    }
}
