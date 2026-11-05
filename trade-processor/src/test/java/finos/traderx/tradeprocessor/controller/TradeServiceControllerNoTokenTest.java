package finos.traderx.tradeprocessor.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import finos.traderx.tradeprocessor.service.TradeService;

@WebMvcTest(controllers = TradeServiceController.class, properties = "trade.processor.order-endpoint.enabled=true")
class TradeServiceControllerNoTokenTest {

	@Autowired
	MockMvc mvc;

	@MockBean
	TradeService tradeService;

	@Test
	void failsClosedWhenNoTokenConfigured() throws Exception {
		mvc.perform(post("/tradeservice/order").header("X-Internal-Token", "")
				.contentType(MediaType.APPLICATION_JSON).content(TradeServiceControllerEnabledTest.ORDER_JSON))
			.andExpect(status().isForbidden());
		verifyNoInteractions(tradeService);
	}
}
