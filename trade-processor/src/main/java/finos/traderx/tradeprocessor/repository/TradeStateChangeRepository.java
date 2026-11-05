package finos.traderx.tradeprocessor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import finos.traderx.tradeprocessor.model.TradeStateChange;

public interface TradeStateChangeRepository extends JpaRepository<TradeStateChange, Long> {

    List<TradeStateChange> findByTradeIdOrderByChangedAtAscIdAsc(String tradeId);

}
