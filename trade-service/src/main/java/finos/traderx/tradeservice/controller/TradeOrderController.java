package finos.traderx.tradeservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import finos.traderx.messaging.PubSubException;
import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.exceptions.ResourceNotFoundException;
import finos.traderx.tradeservice.model.Account;
import finos.traderx.tradeservice.model.Security;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.security.BearerTokenPropagatingInterceptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@CrossOrigin("*")
@RestController
@RequestMapping(value="/trade", produces = "application/json")
public class TradeOrderController {

	private static final Logger log = LoggerFactory.getLogger(TradeOrderController.class);

	@Autowired
	private Publisher<TradeOrder> tradePublisher;
	
	/** Downstream calls carry the caller's own bearer token so account-service can enforce ownership. */
	private RestTemplate restTemplate = new RestTemplateBuilder()
			.additionalInterceptors(new BearerTokenPropagatingInterceptor())
			.build();

	@Value("${reference.data.service.url}")
	private String referenceDataServiceAddress;

	@Value("${account.service.url}")
	private String accountServiceAddress;

	@Operation(description = "Submit a new trade order")
	@PostMapping("/")
	public ResponseEntity<TradeOrder> createTradeOrder(@Parameter(description = "the intendeded trade order") @RequestBody TradeOrder tradeOrder,
			Authentication authentication) {
		log.info("Called createTradeOrder by {}", authentication.getName());
		
		if (!validateTicker(tradeOrder.getSecurity())) 
		{
			throw new ResourceNotFoundException(tradeOrder.getSecurity() + " not found in Reference data service.");
		}
		else if(!validateAccount(tradeOrder.getAccountId()))
		{
			throw new ResourceNotFoundException(tradeOrder.getAccountId() + " not found in Account service.");
		}
		else
		{
			try{
				log.info("Trade is valid. Submitting {}", tradeOrder);
				tradePublisher.publish("/trades",tradeOrder);
				return  ResponseEntity.ok(tradeOrder);
			}  catch (PubSubException e){
				throw new RuntimeException("Failed to publish trade order", e);
			}
		}
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<String> accessDenied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
	}

	private boolean validateTicker(String ticker)
	{
		// Move whole method to a sperate class that handles all reference data 
		// so we can mock it and run without this service up.
		String url = this.referenceDataServiceAddress + "//stocks/" + ticker;
		ResponseEntity<Security> response = null;

		try {
			response = this.restTemplate.getForEntity(url, Security.class);
			log.info("Validate ticker " + response.getBody().toString());
			return true;
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.info(ticker + " not found in reference data service.");
			}
			else {
				log.error(ex.getMessage());
			}
			return false;
		}
	}		
	
	private boolean validateAccount(Integer id)
	{
		// Move whole method to a sperate class that handles all accounts 
		// so we can mock it and run without this service up.

		String url = this.accountServiceAddress + "//account/" + id;
		ResponseEntity<Account> response = null;

		try 
		{
				response = this.restTemplate.getForEntity(url, Account.class);
				log.info("Validate account " + response.getBody().toString());
				return true;
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
				throw new AccessDeniedException("Caller does not own account " + id);
			}
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.info("Account" + id + " not found in account service.");				
			}
			else {
				log.error(ex.getMessage());
			}
			return false;
		}
	}
}