package finos.traderx.accountservice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import finos.traderx.accountservice.exceptions.ResourceNotFoundException;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountMessageMaskingTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    AccountUserRepository accountUserRepository;

    @InjectMocks
    AccountService accountService;

    @InjectMocks
    AccountUserService accountUserService;

    @Test
    void unknownAccountMessageShowsOnlyLastFourDigits() {
        when(accountRepository.findById(22214)).thenReturn(Optional.empty());
        String message = assertThrows(ResourceNotFoundException.class, () -> accountService.getAccountById(22214)).getMessage();
        assertFalse(message.contains("22214"), message);
        assertTrue(message.contains("****2214"), message);
    }

    @Test
    void unknownAccountOnAssignUserMessageShowsOnlyLastFourDigits() {
        when(accountRepository.findById(22214)).thenReturn(Optional.empty());
        AccountUser accountUser = new AccountUser();
        accountUser.setAccountId(22214);
        String message = assertThrows(ResourceNotFoundException.class, () -> accountUserService.upsertAccountUser(accountUser)).getMessage();
        assertFalse(message.contains("22214"), message);
        assertTrue(message.contains("****2214"), message);
    }
}
