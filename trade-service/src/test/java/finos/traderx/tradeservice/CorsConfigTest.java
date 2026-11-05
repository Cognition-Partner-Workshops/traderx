package finos.traderx.tradeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.controller.TradeOrderController;
import finos.traderx.tradeservice.model.TradeOrder;

@WebMvcTest(controllers = {TradeOrderController.class},
		properties = "CORS_ALLOWED_ORIGINS=http://localhost:18093, https://traderx.example.com ,*")
class CorsConfigTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:18093";
	private static final String CONFIGURED_ORIGIN = "https://traderx.example.com";
	private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

	@Autowired
	MockMvc mvc;

	@MockBean
	Publisher<TradeOrder> tradePublisher;

	@Test
	void preflight_post_trade_fromAllowedOrigin_getsAllowOriginHeader() throws Exception {
		mvc.perform(options("/trade/")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
	}

	@Test
	void preflight_post_trade_fromConfiguredOrigin_getsAllowOriginHeader() throws Exception {
		mvc.perform(options("/trade/")
				.header(HttpHeaders.ORIGIN, CONFIGURED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CONFIGURED_ORIGIN));
	}

	@Test
	void preflight_post_trade_fromDisallowedOrigin_isRejectedWithoutAllowOriginHeader() throws Exception {
		mvc.perform(options("/trade/")
				.header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void post_trade_fromDisallowedOrigin_isRejectedBeforeReachingController() throws Exception {
		mvc.perform(post("/trade/")
				.header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		verifyNoInteractions(tradePublisher);
	}

	@Test
	void defaultOrigins_areTheLocalUiOrigins() {
		assertThat(CorsConfig.parseOrigins(CorsConfig.DEFAULT_ALLOWED_ORIGINS))
			.containsExactly("http://localhost:8080", "http://localhost:18093", "http://localhost:18094");
	}

	@Test
	void wildcardAndBlankEntries_areIgnored() {
		assertThat(CorsConfig.parseOrigins(" * , ,https://a.example.com")).containsExactly("https://a.example.com");
	}
}
