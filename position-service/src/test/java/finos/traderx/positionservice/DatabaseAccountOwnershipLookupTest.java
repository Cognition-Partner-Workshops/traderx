package finos.traderx.positionservice;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.junit.jupiter.api.AfterEach;

import finos.traderx.positionservice.service.DatabaseAccountOwnershipLookup;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseAccountOwnershipLookupTest {
	private EmbeddedDatabase database;
	private DatabaseAccountOwnershipLookup lookup;

	@BeforeEach
	void setUp() {
		database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
				.setType(EmbeddedDatabaseType.H2).build();
		JdbcTemplate jdbc = new JdbcTemplate(database);
		jdbc.execute("CREATE TABLE AccountUsers (AccountID INTEGER NOT NULL, Username VARCHAR(15) NOT NULL, PRIMARY KEY (AccountID, Username))");
		jdbc.update("INSERT INTO AccountUsers VALUES (1, 'alice'), (1, 'bob'), (2, 'bob'), (3, 'alice')");
		lookup = new DatabaseAccountOwnershipLookup(jdbc);
	}

	@AfterEach
	void tearDown() {
		database.shutdown();
	}

	@Test
	void readsSharedAccountsForExactUsername() {
		assertEquals(Set.of(1, 3), lookup.findAccountIds("alice"));
		assertEquals(Set.of(1, 2), lookup.findAccountIds("bob"));
	}

	@Test
	void unknownUserHasNoAccounts() {
		assertTrue(lookup.findAccountIds("unknown").isEmpty());
	}

	@Test
	void usernameIsNotInterpretedAsSql() {
		assertTrue(lookup.findAccountIds("' OR '1'='1").isEmpty());
	}
}
