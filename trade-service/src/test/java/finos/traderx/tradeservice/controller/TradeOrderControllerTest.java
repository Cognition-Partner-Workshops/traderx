package finos.traderx.tradeservice.controller;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.model.Account;
import finos.traderx.tradeservice.model.Security;
import finos.traderx.tradeservice.model.TradeOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradeOrderControllerTest {
    private MockMvc mvc;
    private RestTemplate restTemplate;
    private Publisher<TradeOrder> publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        publisher = mock(Publisher.class);
        TradeOrderController controller = new TradeOrderController();
        ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(controller, "tradePublisher", publisher);
        ReflectionTestUtils.setField(controller, "referenceDataServiceAddress", "http://reference");
        ReflectionTestUtils.setField(controller, "accountServiceAddress", "http://accounts");
        when(restTemplate.getForEntity(any(String.class), eq(Security.class)))
                .thenReturn(ResponseEntity.ok(new Security("ABC", "Example")));
        when(restTemplate.getForEntity(any(String.class), eq(Account.class)))
                .thenReturn(ResponseEntity.ok(new Account(1, "Example")));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":1000001}",
        "{\"quantity\":null}", "{\"quantity\":1.5}", "{\"quantity\":2147483648}",
        "{\"side\":null}", "{\"side\":\"Hold\"}", "{\"side\":0}",
        "{\"accountId\":0}", "{\"accountId\":-1}", "{\"accountId\":null}",
        "{\"security\":null}", "{\"security\":\"\"}", "{\"security\":\"abc\"}",
        "{\"security\":\"ABCDEFGHIJK\"}", "{\"security\":\"../admin\"}",
        "{\"security\":\"ABC?x=1\"}", "{\"security\":\"ABC#x\"}",
        "{\"security\":\"%2Fadmin\"}", "{\"security\":\"..\"}"
    })
    void rejectsInvalidFieldsBeforeOutboundCalls(String override) throws Exception {
        String body = "{\"quantity\":1,\"side\":\"Buy\",\"accountId\":1,\"security\":\"ABC\"," + override.substring(1);
        mvc.perform(post("/trade/").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate, publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"quantity\":1,\"side\":\"Buy\",\"accountId\":1}",
        "{\"side\":\"Buy\",\"accountId\":1,\"security\":\"ABC\"}",
        "{\"quantity\":1,\"accountId\":1,\"security\":\"ABC\"}",
        "{\"quantity\":1,\"side\":\"Buy\",\"security\":\"ABC\"}"})
    void rejectsMissingInputBeforeOutboundCalls(String body) throws Exception {
        mvc.perform(post("/trade/").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate, publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Buy", "Sell"})
    void acceptsValidOrder(String side) throws Exception {
        String body = "{\"quantity\":1000000,\"side\":\"" + side + "\",\"accountId\":1,\"security\":\"BRK.B-1\"}";
        mvc.perform(post("/trade/").contentType("application/json").content(body))
                .andExpect(status().isOk());
        verify(restTemplate).getForEntity("http://reference//stocks/BRK.B-1", Security.class);
        verify(restTemplate).getForEntity("http://accounts//account/1", Account.class);
        verify(publisher).publish(eq("/trades"), any(TradeOrder.class));
        verifyNoMoreInteractions(restTemplate, publisher);
    }
}
