package finos.traderx.positionservice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AccountMaskingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tradeResponseShowsOnlyLastFourDigitsOfAccount() {
        Trade trade = new Trade();
        trade.setAccountId(22214);
        assertEquals("****2214", objectMapper.valueToTree(trade).get("accountId").asText());
    }

    @Test
    void positionResponseShowsOnlyLastFourDigitsOfAccount() {
        Position position = new Position();
        position.setAccountId(22214);
        assertEquals("****2214", objectMapper.valueToTree(position).get("accountId").asText());
    }
}
