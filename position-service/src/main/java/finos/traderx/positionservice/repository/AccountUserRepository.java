package finos.traderx.positionservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import finos.traderx.positionservice.model.AccountUser;
import finos.traderx.positionservice.model.AccountUserID;

public interface AccountUserRepository extends JpaRepository<AccountUser, AccountUserID> {

	boolean existsByAccountIdAndUsername(Integer accountId, String username);
}
