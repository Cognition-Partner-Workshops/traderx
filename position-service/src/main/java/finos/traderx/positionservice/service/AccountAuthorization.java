package finos.traderx.positionservice.service;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountAuthorization {
	public static final String USER_HEADER = "X-Authenticated-User";

	private final AccountOwnershipLookup ownershipLookup;

	public AccountAuthorization(AccountOwnershipLookup ownershipLookup) {
		this.ownershipLookup = ownershipLookup;
	}

	public Set<Integer> ownedAccounts(String username) {
		if (username == null || username.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
		}
		return ownershipLookup.findAccountIds(username);
	}

	public void requireOwnership(String username, int accountId) {
		if (!ownedAccounts(username).contains(accountId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access denied");
		}
	}
}
