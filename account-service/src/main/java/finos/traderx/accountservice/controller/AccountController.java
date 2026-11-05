package finos.traderx.accountservice.controller;

import java.util.List;

import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.security.AccountAccess;
import finos.traderx.accountservice.service.AccountService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin("*")
@RestController
@RequestMapping(value="/account", produces="application/json")
public class AccountController {

	@Autowired
	AccountService accountService;

	@Autowired
	AccountAccess accountAccess;

	@GetMapping("/{id}")
	@PreAuthorize("@accountAccess.canRead(authentication, #id)")
	public ResponseEntity<Account> getAccountById(@PathVariable int id) {
		Account retVal = this.accountService.getAccountById(id);
		return ResponseEntity.ok(retVal);
	}

	@PostMapping("/")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Account> createAccount(@RequestBody Account account) {
		return ResponseEntity.ok(this.accountService.upsertAccount(account));
	}

	@PutMapping("/")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Account> updateAccount(@RequestBody Account account) {
		return ResponseEntity.ok(this.accountService.upsertAccount(account));
	}

	@GetMapping("/")
	public ResponseEntity<List<Account>> getAllAccount(Authentication authentication) {
		if (this.accountAccess.isPrivileged(authentication)) {
			return ResponseEntity.ok(this.accountService.getAllAccount());
		}
		return ResponseEntity.ok(this.accountService.getAccountsForUser(authentication.getName()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<String> resourceNotFoundExceptionMapper(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<String> accessDenied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Caller is not permitted to access this resource");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> generalError(Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
	}
}
