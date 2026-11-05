package finos.traderx.tradeprocessor.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import finos.traderx.tradeprocessor.model.TradeStatusHistory;

/**
 * Append-only: exposes insert and read, no update or delete.
 */
public interface TradeStatusHistoryRepository extends Repository<TradeStatusHistory, Long> {

    TradeStatusHistory save(TradeStatusHistory record);

    List<TradeStatusHistory> findByTradeIdOrderByIdAsc(String tradeId);
}
