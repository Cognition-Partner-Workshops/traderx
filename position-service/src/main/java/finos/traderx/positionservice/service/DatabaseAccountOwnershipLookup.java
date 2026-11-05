package finos.traderx.positionservice.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseAccountOwnershipLookup implements AccountOwnershipLookup {
	private final JdbcTemplate jdbcTemplate;

	public DatabaseAccountOwnershipLookup(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Set<Integer> findAccountIds(String username) {
		return new HashSet<>(jdbcTemplate.queryForList(
				"SELECT ACCOUNTID FROM ACCOUNTUSERS WHERE USERNAME = ?", Integer.class, username));
	}
}
