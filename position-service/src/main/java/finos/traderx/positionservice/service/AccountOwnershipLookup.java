package finos.traderx.positionservice.service;

import java.util.Set;

public interface AccountOwnershipLookup {
	Set<Integer> findAccountIds(String username);
}
