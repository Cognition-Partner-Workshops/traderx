package finos.traderx.positionservice.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Read-only view of the account/user mapping owned by account-service, used for ownership checks. */
@Entity
@Table(name = "ACCOUNTUSERS")
@IdClass(AccountUserID.class)
public class AccountUser implements Serializable {

	@Id
	@Column(name = "AccountID")
	Integer accountId;

	@Id
	@Column(name = "Username")
	String username;

	public Integer getAccountId() {
		return accountId;
	}

	public String getUsername() {
		return username;
	}
}
