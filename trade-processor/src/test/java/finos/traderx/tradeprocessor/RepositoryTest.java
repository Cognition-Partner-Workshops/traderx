package finos.traderx.tradeprocessor;

import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class RepositoryTest {
    @Autowired TradeRepository trades;
    @Autowired PositionRepository positions;
    @Autowired TestEntityManager em;

    @Test void tradeQueryFiltersByAccountAndRoundTripsBookingFields() {
        trade("one", 7, TradeSide.Buy);
        trade("two", 8, TradeSide.Sell);
        em.flush();
        em.clear();
        assertThat(trades.findByAccountId(7)).singleElement().satisfies(t -> {
            assertThat(t.getId()).isEqualTo("one");
            assertThat(t.getSide()).isEqualTo(TradeSide.Buy);
            assertThat(t.getState()).isEqualTo(TradeState.Settled);
            assertThat(t.getQuantity()).isEqualTo(12);
        });
        assertThat(trades.findByAccountId(99)).isEmpty();
    }

    @Test void positionQueriesUseAccountAndSecurityAndPersistUpdates() {
        position(7, "IBM", 12);
        position(7, "MSFT", 5);
        position(8, "IBM", 90);
        em.flush();
        em.clear();
        assertThat(positions.findByAccountId(7)).extracting(Position::getSecurity).containsExactlyInAnyOrder("IBM", "MSFT");
        Position found = positions.findByAccountIdAndSecurity(7, "IBM");
        assertThat(found.getQuantity()).isEqualTo(12);
        found.setQuantity(-2);
        positions.saveAndFlush(found);
        em.clear();
        assertThat(positions.findByAccountIdAndSecurity(7, "IBM").getQuantity()).isEqualTo(-2);
        assertThat(positions.findByAccountIdAndSecurity(8, "IBM").getQuantity()).isEqualTo(90);
        assertThat(positions.findByAccountIdAndSecurity(7, "NONE")).isNull();
        assertThat(positions.findByAccountId(99)).isEmpty();
    }

    void trade(String id, int account, TradeSide side) {
        Trade t = new Trade();
        t.setId(id); t.setAccountId(account); t.setSecurity("IBM");
        t.setSide(side); t.setState(TradeState.Settled); t.setQuantity(12);
        em.persist(t);
    }

    void position(int account, String security, int quantity) {
        Position p = new Position();
        p.setAccountId(account); p.setSecurity(security); p.setQuantity(quantity);
        em.persist(p);
    }
}
