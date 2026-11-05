package finos.traderx.tradeservice;

import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.controller.TradeOrderController;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.model.TradeSide;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

class OrderReferenceUriTest {
    TradeOrderController controller;
    MockRestServiceServer server;
    Publisher<TradeOrder> publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        controller = new TradeOrderController();
        RestTemplate template = new RestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        publisher = mock(Publisher.class);
        ReflectionTestUtils.setField(controller, "restTemplate", template);
        ReflectionTestUtils.setField(controller, "tradePublisher", publisher);
        ReflectionTestUtils.setField(controller, "referenceDataServiceAddress", "http://reference.test");
        ReflectionTestUtils.setField(controller, "accountServiceAddress", "http://account.test");
    }

    @Test
    void validOrderUsesExactReferenceUris() throws Exception {
        expect("IBM");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/trade/").contentType(MediaType.APPLICATION_JSON)
            .content("{\"quantity\":10,\"side\":\"Buy\",\"accountId\":22,\"security\":\"IBM\"}"))
            .andExpect(status().isOk());
        server.verify();
        verify(publisher).publish(eq("/trades"), any(TradeOrder.class));
    }

    @Test
    void unusualValidTickerRemainsOnePathSegment() {
        TradeOrder order = new TradeOrder("id", 22, "BRK.B-A", TradeSide.Buy, 10);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(order).isEmpty());
        }
        expect("BRK.B-A");
        assertEquals(200, controller.createTradeOrder(order).getStatusCode().value());
        server.verify();
    }

    private void expect(String ticker) {
        server.expect(requestTo("http://reference.test/stocks/" + ticker))
            .andRespond(withSuccess("{\"ticker\":\"IBM\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://account.test/account/22"))
            .andRespond(withSuccess("{\"id\":22}", MediaType.APPLICATION_JSON));
    }
}
