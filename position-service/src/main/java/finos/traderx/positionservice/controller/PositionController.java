package finos.traderx.positionservice.controller;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import finos.traderx.positionservice.model.Position;
import finos.traderx.positionservice.service.PositionService;
import finos.traderx.positionservice.service.AccountAuthorization;

@CrossOrigin("*")
@RestController
@RequestMapping(value="/positions", produces="application/json")
public class PositionController {

	@Autowired
	PositionService positionService;
	@Autowired
	AccountAuthorization authorization;
 

	@GetMapping("/{accountId}")
	public ResponseEntity<List<Position>> getByAccountId(@PathVariable int accountId,
			@RequestHeader(value = AccountAuthorization.USER_HEADER, required = false) String username) {
		authorization.requireOwnership(username, accountId);
		List<Position> retVal = this.positionService.getPositionsByAccountID(accountId);
		return ResponseEntity.ok(retVal);
	}

	@GetMapping("/")
	public ResponseEntity<List<Position>> getAllPositions(
			@RequestHeader(value = AccountAuthorization.USER_HEADER, required = false) String username) {
		Set<Integer> ownedAccounts = authorization.ownedAccounts(username);
		return ResponseEntity.ok(this.positionService.getAllPositions().stream()
				.filter(record -> ownedAccounts.contains(record.getAccountId())).toList());
	}


	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> generalError(Exception e) {
		if (e instanceof ResponseStatusException statusException) {
			return ResponseEntity.status(statusException.getStatusCode()).body(statusException.getReason());
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
	}
}
