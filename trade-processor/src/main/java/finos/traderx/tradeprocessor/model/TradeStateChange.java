package finos.traderx.tradeprocessor.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per change of a trade's state. Rows are only ever appended, so the
 * full New / Processing / Settled / Cancelled history of a trade survives, each
 * stamped with the UTC instant it happened.
 */
@Entity
@Table(name = "TRADESTATEHISTORY")
public class TradeStateChange implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(length = 100, name = "TRADEID", nullable = false)
	private String tradeId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20, name = "STATE", nullable = false)
	private TradeState state;

	@Column(name = "CHANGEDAT", nullable = false)
	private Instant changedAt;

	public TradeStateChange() {}

	public TradeStateChange(String tradeId, TradeState state, Instant changedAt) {
		this.tradeId = tradeId;
		this.state = state;
		this.changedAt = changedAt;
	}

	public Long getId() {
		return id;
	}

	public String getTradeId() {
		return tradeId;
	}

	public TradeState getState() {
		return state;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	@Override
	public String toString() {
		return "TradeStateChange[trade=" + tradeId + ", state=" + state + ", changedAt=" + changedAt + "]";
	}
}
