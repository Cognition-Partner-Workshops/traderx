package finos.traderx.accountservice.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import finos.traderx.accountservice.repository.AccountUserRepository;

/**
 * Ownership rule: a caller may read an account when it is an ADMIN or SERVICE,
 * or when its identity (JWT subject) is mapped to that account in ACCOUNTUSERS.
 */
@Component("accountAccess")
public class AccountAccess {

	public static final String ROLE_ADMIN = SecurityConfig.ROLE_PREFIX + "ADMIN";
	public static final String ROLE_SERVICE = SecurityConfig.ROLE_PREFIX + "SERVICE";

	@Autowired
	AccountUserRepository accountUserRepository;

	public boolean isPrivileged(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()) || ROLE_SERVICE.equals(a.getAuthority()));
	}

	public boolean canRead(Authentication authentication, int accountId) {
		if (authentication == null) {
			return false;
		}
		return isPrivileged(authentication)
			|| accountUserRepository.existsByAccountIdAndUsername(accountId, authentication.getName());
	}
}
