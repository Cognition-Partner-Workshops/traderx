package finos.traderx.tradeservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.exceptions.ResourceNotFoundException;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.model.TradeSide;

@WebMvcTest(controllers = { TradeOrderController.class, DocsController.class })
@TestPropertySource(properties = {
        "reference.data.service.url=http://refdata.test",
        "account.service.url=http://accounts.test"
})
class TradeOrderControllerTest {

    private static final String STOCK_URL = "http://refdata.test/stocks/";
    private static final String ACCOUNT_URL = "http://accounts.test/account/";
    private static final String SECURITY_JSON = "{\"ticker\":\"IBM\",\"companyName\":\"International Business Machines\"}";
    private static final String ACCOUNT_JSON = "{\"id\":22214,\"displayName\":\"Test Account\"}";
    private static final String ORDER_JSON = "{\"id\":\"order-1\",\"security\":\"IBM\",\"quantity\":100,\"accountId\":22214,\"side\":\"Buy\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeOrderController controller;

    @MockBean
    private Publisher<TradeOrder> tradePublisher;

    private MockRestServiceServer upstream;

    @BeforeEach
    void bindUpstreamServices() {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(controller, "restTemplate");
        upstream = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private void tickerResponds(String ticker, org.springframework.test.web.client.ResponseCreator response) {
        upstream.expect(requestTo(STOCK_URL + ticker)).andExpect(method(HttpMethod.GET)).andRespond(response);
    }

    private void accountResponds(int accountId, org.springframework.test.web.client.ResponseCreator response) {
        upstream.expect(requestTo(ACCOUNT_URL + accountId)).andExpect(method(HttpMethod.GET)).andRespond(response);
    }

    private org.springframework.test.web.servlet.ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post("/trade/").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validOrderIsPublishedToTradesTopicAndEchoedBack() throws Exception {
        tickerResponds("IBM", withSuccess(SECURITY_JSON, MediaType.APPLICATION_JSON));
        accountResponds(22214, withSuccess(ACCOUNT_JSON, MediaType.APPLICATION_JSON));

        submit(ORDER_JSON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.security").value("IBM"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.accountId").value(22214))
                .andExpect(jsonPath("$.side").value("Buy"));

        ArgumentCaptor<TradeOrder> published = ArgumentCaptor.forClass(TradeOrder.class);
        verify(tradePublisher).publish(eq("/trades"), published.capture());
        assertThat(published.getValue().getSecurity()).isEqualTo("IBM");
        assertThat(published.getValue().getAccountId()).isEqualTo(22214);
        assertThat(published.getValue().getQuantity()).isEqualTo(100);
        assertThat(published.getValue().getSide()).isEqualTo(TradeSide.Buy);
        upstream.verify();
    }

    @Test
    void unknownTickerIsRejectedWith404WithoutCheckingAccountOrPublishing() throws Exception {
        tickerResponds("NOPE", withResourceNotFound());

        MvcResult result = submit(ORDER_JSON.replace("IBM", "NOPE"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("NOPE not found in Reference data service.");
        verifyNoInteractions(tradePublisher);
        upstream.verify();
    }

    @Test
    void nonNotFoundClientErrorFromReferenceDataAlsoRejectsTicker() throws Exception {
        tickerResponds("IBM", withBadRequest());

        MvcResult result = submit(ORDER_JSON).andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResolvedException()).hasMessage("IBM not found in Reference data service.");
        verifyNoInteractions(tradePublisher);
        upstream.verify();
    }

    @Test
    void unknownAccountIsRejectedWith404WithoutPublishing() throws Exception {
        tickerResponds("IBM", withSuccess(SECURITY_JSON, MediaType.APPLICATION_JSON));
        accountResponds(999, withResourceNotFound());

        MvcResult result = submit(ORDER_JSON.replace("22214", "999"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("999 not found in Account service.");
        verifyNoInteractions(tradePublisher);
        upstream.verify();
    }

    @Test
    void nonNotFoundClientErrorFromAccountServiceAlsoRejectsAccount() throws Exception {
        tickerResponds("IBM", withSuccess(SECURITY_JSON, MediaType.APPLICATION_JSON));
        accountResponds(22214, withBadRequest());

        MvcResult result = submit(ORDER_JSON).andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResolvedException()).hasMessage("22214 not found in Account service.");
        verifyNoInteractions(tradePublisher);
        upstream.verify();
    }

    @Test
    void referenceDataServerErrorIsNotTreatedAsUnknownTickerAndPropagates() {
        tickerResponds("IBM", withServerError());

        assertThatThrownBy(() -> submit(ORDER_JSON))
                .hasRootCauseInstanceOf(HttpServerErrorException.InternalServerError.class);
        verifyNoInteractions(tradePublisher);
        upstream.verify();
    }

    @Test
    void publishFailureSurfacesAsServerErrorWrappingPubSubException() throws Exception {
        tickerResponds("IBM", withSuccess(SECURITY_JSON, MediaType.APPLICATION_JSON));
        accountResponds(22214, withSuccess(ACCOUNT_JSON, MediaType.APPLICATION_JSON));
        PubSubException cause = new PubSubException("feed down");
        doThrow(cause).when(tradePublisher).publish(anyString(), any(TradeOrder.class));

        assertThatThrownBy(() -> submit(ORDER_JSON))
                .cause()
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to publish trade order")
                .cause()
                .isSameAs(cause);
        upstream.verify();
    }

    @Test
    void rootRedirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("swagger-ui.html"));
    }
}
