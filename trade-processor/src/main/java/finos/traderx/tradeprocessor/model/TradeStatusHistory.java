package finos.traderx.tradeprocessor.model;

import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only record of a single trade state change, timestamped in UTC.
 */
@Entity
@Immutable
@Table(name = "TRADESTATUSHISTORY")
public class TradeStatusHistory implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(length = 100, name = "TRADEID", nullable = false, updatable = false)
	private String tradeId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, name = "STATE", nullable = false, updatable = false)
	private TradeState state;

	@JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
	@Column(name = "CHANGEDAT", nullable = false, updatable = false)
	private Instant changedAt;

	protected TradeStatusHistory() {
	}

	public TradeStatusHistory(String tradeId, TradeState state, Instant changedAt) {
		this.tradeId = tradeId;
		this.state = state;
		this.changedAt = changedAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getTradeId() {
		return this.tradeId;
	}

	public TradeState getState() {
		return this.state;
	}

	public Instant getChangedAt() {
		return this.changedAt;
	}
}
