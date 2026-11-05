package finos.traderx.accountservice.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import finos.traderx.records.AccountReferences;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "Accountusers")
@IdClass(AccountUserID.class)
public class AccountUser implements Serializable {

	@Id
	@Column(name = "AccountID")
	Integer accountId;

	@Id
	@Column(name = "Username")
	String username;

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@JsonSerialize(using = AccountReferences.Serializer.class)
	@JsonDeserialize(using = AccountReferences.Deserializer.class)
	public Integer getAccountId() {
		return this.accountId;
	}

	@com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	public String getAccountNumber() {
		return AccountReferences.masked(accountId);
	}

	public void setAccountId(Integer accountId) {
		this.accountId = accountId;
	}

}
