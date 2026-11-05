package finos.traderx.tradeprocessor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import finos.traderx.tradeprocessor.model.TradeBookingResult;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.service.TradeService;

@WebMvcTest(controllers = TradeServiceController.class, properties = {
		"trade.processor.order-endpoint.enabled=true",
		"trade.processor.order-endpoint.token=test-internal-token" })
class TradeServiceControllerEnabledTest {

	static final String ORDER_JSON = "{\"id\":\"1\",\"state\":\"New\",\"security\":\"IBM\",\"quantity\":10,\"accountId\":22214,\"side\":\"Buy\"}";

	@Autowired
	MockMvc mvc;

	@MockBean
	TradeService tradeService;

	@Test
	void rejectsCallerWithoutToken() throws Exception {
		mvc.perform(post("/tradeservice/order").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
			.andExpect(status().isForbidden());
		verifyNoInteractions(tradeService);
	}

	@Test
	void rejectsCallerWithWrongToken() throws Exception {
		mvc.perform(post("/tradeservice/order").header("X-Internal-Token", "wrong")
				.contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
			.andExpect(status().isForbidden());
		verifyNoInteractions(tradeService);
	}

	@Test
	void acceptsCallerWithToken() throws Exception {
		when(tradeService.processTrade(any(TradeOrder.class))).thenReturn(new TradeBookingResult(null, null));
		mvc.perform(post("/tradeservice/order").header("X-Internal-Token", "test-internal-token")
				.contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
			.andExpect(status().isOk());
		verify(tradeService).processTrade(any(TradeOrder.class));
	}
}
