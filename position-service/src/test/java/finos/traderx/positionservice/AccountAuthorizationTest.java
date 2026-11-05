package finos.traderx.positionservice;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import finos.traderx.positionservice.controller.PositionController;
import finos.traderx.positionservice.controller.TradeController;
import finos.traderx.positionservice.model.Position;
import finos.traderx.positionservice.model.Trade;
import finos.traderx.positionservice.service.AccountOwnershipLookup;
import finos.traderx.positionservice.service.PositionService;
import finos.traderx.positionservice.service.TradeService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PositionController.class, TradeController.class})
@ComponentScan(basePackages = "finos.traderx.positionservice.service", useDefaultFilters = false,
		includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.AccountAuthorization"))
class AccountAuthorizationTest {
	@Autowired MockMvc mvc;
	@MockBean PositionService positions;
	@MockBean TradeService trades;
	@MockBean AccountOwnershipLookup ownership;

	@ParameterizedTest
	@ValueSource(strings = {"positions", "trades"})
	void ownAccountReturns200(String resource) throws Exception {
		when(ownership.findAccountIds("alice")).thenReturn(Set.of(1));
		mvc.perform(get("/" + resource + "/1").header("X-Authenticated-User", "alice"))
				.andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions", "trades"})
	void crossOwnerReturns403(String resource) throws Exception {
		when(ownership.findAccountIds("alice")).thenReturn(Set.of(1));
		mvc.perform(get("/" + resource + "/2").header("X-Authenticated-User", "alice"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(positions, trades);
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions/1", "trades/1", "positions/", "trades/"})
	void missingHeaderReturns401(String path) throws Exception {
		mvc.perform(get("/" + path)).andExpect(status().isUnauthorized());
		verifyNoInteractions(ownership, positions, trades);
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions/1", "trades/1", "positions/", "trades/"})
	void blankHeaderReturns401(String path) throws Exception {
		mvc.perform(get("/" + path).header("X-Authenticated-User", "  "))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(ownership, positions, trades);
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions", "trades"})
	void collectionOnlyContainsOwnedAccounts(String resource) throws Exception {
		when(ownership.findAccountIds("alice")).thenReturn(Set.of(1));
		seedRecords();
		mvc.perform(get("/" + resource + "/").header("X-Authenticated-User", "alice"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].accountId").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions", "trades"})
	void noOwnedAccountsReturnsEmptyCollection(String resource) throws Exception {
		when(ownership.findAccountIds("alice")).thenReturn(Set.of());
		seedRecords();
		mvc.perform(get("/" + resource + "/").header("X-Authenticated-User", "alice"))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"positions/1", "trades/1", "positions/", "trades/"})
	void lookupFailureReturns500WithoutReadingRecords(String path) throws Exception {
		when(ownership.findAccountIds("alice")).thenThrow(new IllegalStateException("Lookup unavailable"));
		mvc.perform(get("/" + path).header("X-Authenticated-User", "alice"))
				.andExpect(status().isInternalServerError());
		verifyNoInteractions(positions, trades);
	}

	private void seedRecords() {
		Position ownPosition = new Position();
		ownPosition.setAccountId(1);
		Position otherPosition = new Position();
		otherPosition.setAccountId(2);
		when(positions.getAllPositions()).thenReturn(List.of(ownPosition, otherPosition));
		Trade ownTrade = new Trade();
		ownTrade.setAccountId(1);
		Trade otherTrade = new Trade();
		otherTrade.setAccountId(2);
		when(trades.getAllTrades()).thenReturn(List.of(ownTrade, otherTrade));
	}
}
