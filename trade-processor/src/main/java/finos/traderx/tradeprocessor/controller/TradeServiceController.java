package finos.traderx.tradeprocessor.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import finos.traderx.tradeprocessor.model.TradeBookingResult;
import finos.traderx.tradeprocessor.model.TradeOrder;
import finos.traderx.tradeprocessor.service.TradeService;

@CrossOrigin("*")
@RestController
@RequestMapping("/tradeservice")
public class TradeServiceController {

	private static final Logger log = LoggerFactory.getLogger(TradeServiceController.class);

	@Autowired
	TradeService tradeService;
 

	/** Booking is a back-office operation: only trusted services (or admins) may call it directly. */
	@PostMapping("/order")
	@PreAuthorize("hasAnyRole('ADMIN', 'SERVICE')")
	public ResponseEntity<TradeBookingResult> processOrder(@RequestBody TradeOrder order) {
		TradeBookingResult result= tradeService.processTrade(order);
		return ResponseEntity.ok(result);
	}

	

}
