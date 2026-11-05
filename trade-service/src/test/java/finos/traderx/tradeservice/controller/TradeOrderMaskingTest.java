package finos.traderx.tradeservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import finos.traderx.tradeservice.exceptions.ResourceNotFoundException;
import finos.traderx.tradeservice.model.Account;
import finos.traderx.tradeservice.model.Security;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.model.TradeSide;

@JsonTest
class TradeOrderMaskingTest {

    @Autowired
    ObjectMapper httpObjectMapper;

    @Test
    void tradeOrderResponseShowsOnlyLastFourDigitsOfAccount() throws Exception {
        TradeOrder order = new TradeOrder(null, 22214, "IBM", TradeSide.Buy, 100);
        assertEquals("****2214", httpObjectMapper.valueToTree(order).get("accountId").asText());
    }

    @Test
    void tradeOrderPublishedToProcessorKeepsFullAccount() throws Exception {
        TradeOrder order = new TradeOrder(null, 22214, "IBM", TradeSide.Buy, 100);
        assertEquals(22214, new ObjectMapper().valueToTree(order).get("accountId").asInt());
    }

    @Test
    void unknownAccountMessageShowsOnlyLastFourDigits() {
        TradeOrderController controller = new TradeOrderController();
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForEntity(contains("/stocks/"), eq(Security.class)))
                .thenReturn(ResponseEntity.ok(new Security("IBM", "IBM")));
        when(restTemplate.getForEntity(contains("/account/"), eq(Account.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(controller, "referenceDataServiceAddress", "http://ref");
        ReflectionTestUtils.setField(controller, "accountServiceAddress", "http://acct");

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> controller.createTradeOrder(new TradeOrder(null, 22214, "IBM", TradeSide.Buy, 100)));
        assertFalse(e.getMessage().contains("22214"), e.getMessage());
        assertTrue(e.getMessage().contains("****2214"), e.getMessage());
    }
}
