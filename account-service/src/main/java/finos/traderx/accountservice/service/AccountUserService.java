package finos.traderx.accountservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.exceptions.AccountUserConflictException;
import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountUserService {

	@Autowired
	AccountUserRepository accountUserRepository;

	@Autowired
	AccountRepository accountRepository;

	public List<AccountUser> getAllAccountUsers() {
		List<AccountUser> accountUsers = new ArrayList<AccountUser>();
		this.accountUserRepository.findAll().forEach(accountUser -> accountUsers.add(accountUser));
		return accountUsers;
	}

	public AccountUser getAccountUserById(int id) throws ResourceNotFoundException {
		Optional<AccountUser> accountUser = this.accountUserRepository.findById(Integer.valueOf(id));
		if (accountUser.isEmpty()) {
			throw new ResourceNotFoundException("AccountUser with id " + id + "not found");
		}
		return accountUser.get();
	}

	public AccountUser upsertAccountUser(AccountUser accountUser) {
		Optional<Account> account = this.accountRepository.findById(accountUser.getAccountId());
		if (account.isEmpty()) {
			throw new ResourceNotFoundException("Account with id " + accountUser.getAccountId() + "not found");
		}
		return this.accountUserRepository.save(accountUser);
	}

	public AccountUser createAccountUser(AccountUser accountUser) {
		if (!this.accountRepository.existsById(accountUser.getAccountId())) {
			throw new ResourceNotFoundException("Account not found");
		}
		if (this.accountUserRepository.existsByAccountIdAndUsername(accountUser.getAccountId(), accountUser.getUsername())) {
			throw new AccountUserConflictException();
		}
		return this.accountUserRepository.save(accountUser);
	}

	public AccountUser updateAccountUser(AccountUser accountUser) {
		if (!this.accountUserRepository.existsByAccountIdAndUsername(accountUser.getAccountId(), accountUser.getUsername())) {
			throw new ResourceNotFoundException("Account user not found");
		}
		return upsertAccountUser(accountUser);
	}

}
