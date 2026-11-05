package finos.traderx.accountservice.model;

import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
	@NotNull
	@Positive
	Integer accountId;

	@Id
	@Column(name = "Username")
	@NotBlank
	@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$")
	String username;

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Integer getAccountId() {
		return this.accountId;
	}

	public void setAccountId(Integer accountId) {
		this.accountId = accountId;
	}

}
