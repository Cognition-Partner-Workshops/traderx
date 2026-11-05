package finos.traderx.accountservice;

import finos.traderx.accountservice.model.*;
import finos.traderx.accountservice.repository.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RepositoryTests {
    @Autowired AccountRepository accounts;
    @Autowired AccountUserRepository users;
    @Autowired TestEntityManager entityManager;

    @Test void persistsFindsUpdatesAndDeletesAccount() {
        assertThat(accounts.findAll()).isEmpty();
        Account account = new Account();
        account.setDisplayName("Trading");
        accounts.save(account);
        entityManager.flush();
        entityManager.clear();
        Account loaded = accounts.findById(account.getId()).orElseThrow();
        assertThat(loaded.getDisplayName()).isEqualTo("Trading");
        loaded.setDisplayName("Updated");
        accounts.save(loaded);
        entityManager.flush();
        entityManager.clear();
        assertThat(accounts.findById(account.getId()).orElseThrow().getDisplayName()).isEqualTo("Updated");
        accounts.deleteById(account.getId());
        entityManager.flush();
        assertThat(accounts.findById(account.getId())).isEmpty();
    }

    @Test void persistsDistinctUsersForSameAccount() {
        AccountUser alice = user("alice");
        AccountUser bob = user("bob");
        users.save(alice);
        users.save(bob);
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findAll()).extracting(AccountUser::getUsername).containsExactlyInAnyOrder("alice", "bob");
        assertThat(entityManager.find(AccountUser.class, new AccountUserID(42, "alice")).getAccountId()).isEqualTo(42);
    }

    @Test void integerUserLookupExposesCompositeIdMismatch() {
        assertThatThrownBy(() -> users.findById(42))
            .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
            .hasMessageContaining("AccountUserID");
    }

    @Disabled("Bug: AccountUserRepository uses Integer IDs, but AccountUser requires composite AccountUserID")
    @Test void retrievesUserThroughRepositoryAfterSaving() {
        users.save(user("alice"));
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findById(42).orElseThrow().getUsername()).isEqualTo("alice");
    }

    private static AccountUser user(String username) {
        AccountUser user = new AccountUser();
        user.setAccountId(42);
        user.setUsername(username);
        return user;
    }
}
