package finos.traderx.tradeprocessor.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import finos.traderx.tradeprocessor.model.TradeBookingResult;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.service.TradeService;

/**
 * Internal-only endpoint that books an order directly, bypassing trade-service validation.
 * Disabled unless trade.processor.order-endpoint.enabled=true, and then requires the
 * shared token from trade.processor.order-endpoint.token in the X-Internal-Token header.
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/tradeservice")
@ConditionalOnProperty(name = "trade.processor.order-endpoint.enabled", havingValue = "true")
public class TradeServiceController {

	public static final String TOKEN_HEADER = "X-Internal-Token";

	private static final Logger log = LoggerFactory.getLogger(TradeServiceController.class);

	@Autowired
	TradeService tradeService;

	@Value("${trade.processor.order-endpoint.token:}")
	private String internalToken;

	@PostMapping("/order")
	public ResponseEntity<TradeBookingResult> processOrder(
			@RequestHeader(name = TOKEN_HEADER, required = false) String token,
			@RequestBody TradeOrder order) {
		if (!isAuthorized(token)) {
			log.warn("Rejected unauthorized call to /tradeservice/order");
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		TradeBookingResult result= tradeService.processTrade(order);
		return ResponseEntity.ok(result);
	}

	private boolean isAuthorized(String token) {
		if (internalToken == null || internalToken.isBlank() || token == null) {
			return false;
		}
		return MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
				token.getBytes(StandardCharsets.UTF_8));
	}
}
