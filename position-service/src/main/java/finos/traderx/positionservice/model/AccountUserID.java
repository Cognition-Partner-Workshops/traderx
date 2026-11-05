package finos.traderx.positionservice.model;

import java.io.Serializable;
import java.util.Objects;

public class AccountUserID implements Serializable {
	private Integer accountId;
	private String username;

	public AccountUserID() {
	}

	public AccountUserID(Integer accountId, String username) {
		this.accountId = accountId;
		this.username = username;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AccountUserID other)) return false;
		return Objects.equals(accountId, other.accountId) && Objects.equals(username, other.username);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accountId, username);
	}
}
