package finos.traderx.accountservice;

import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;
import finos.traderx.accountservice.service.AccountService;
import finos.traderx.accountservice.service.AccountUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTests {
    @Mock AccountRepository accounts;
    @Mock AccountUserRepository users;
    @InjectMocks AccountService accountService;
    @InjectMocks AccountUserService userService;

    @Test void listsAccountsInRepositoryOrder() {
        Account first = new Account();
        Account second = new Account();
        when(accounts.findAll()).thenReturn(List.of(first, second));
        assertThat(accountService.getAllAccount()).containsExactly(first, second);
    }

    @Test void listsEmptyAccounts() {
        when(accounts.findAll()).thenReturn(List.of());
        assertThat(accountService.getAllAccount()).isEmpty();
    }

    @Test void findsAccount() {
        Account account = new Account();
        when(accounts.findById(42)).thenReturn(Optional.of(account));
        assertThat(accountService.getAccountById(42)).isSameAs(account);
    }

    @Test void rejectsMissingAccount() {
        when(accounts.findById(42)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getAccountById(42))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Account with id 42not found");
    }

    @Test void returnsPersistedAccount() {
        Account input = new Account();
        Account persisted = new Account();
        when(accounts.save(input)).thenReturn(persisted);
        assertThat(accountService.upsertAccount(input)).isSameAs(persisted);
    }

    @Test void propagatesPersistenceFailure() {
        Account input = new Account();
        when(accounts.save(input)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> accountService.upsertAccount(input))
            .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }

    @Test void listsUsersInRepositoryOrder() {
        AccountUser first = new AccountUser();
        AccountUser second = new AccountUser();
        when(users.findAll()).thenReturn(List.of(first, second));
        assertThat(userService.getAllAccountUsers()).containsExactly(first, second);
    }

    @Test void listsEmptyUsers() {
        when(users.findAll()).thenReturn(List.of());
        assertThat(userService.getAllAccountUsers()).isEmpty();
    }

    @Test void findsUser() {
        AccountUser user = new AccountUser();
        when(users.findById(42)).thenReturn(Optional.of(user));
        assertThat(userService.getAccountUserById(42)).isSameAs(user);
    }

    @Test void rejectsMissingUser() {
        when(users.findById(42)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getAccountUserById(42))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("AccountUser with id 42not found");
    }

    @Test void savesUserOnlyWhenAccountExists() {
        AccountUser input = new AccountUser();
        input.setAccountId(42);
        AccountUser saved = new AccountUser();
        when(accounts.findById(42)).thenReturn(Optional.of(new Account()));
        when(users.save(input)).thenReturn(saved);
        assertThat(userService.upsertAccountUser(input)).isSameAs(saved);
        var order = inOrder(accounts, users);
        order.verify(accounts).findById(42);
        order.verify(users).save(input);
    }

    @Test void rejectsUserForMissingAccountWithoutSaving() {
        AccountUser input = new AccountUser();
        input.setAccountId(42);
        when(accounts.findById(42)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.upsertAccountUser(input))
            .isInstanceOf(ResourceNotFoundException.class).hasMessage("Account with id 42not found");
        verifyNoInteractions(users);
    }
}
