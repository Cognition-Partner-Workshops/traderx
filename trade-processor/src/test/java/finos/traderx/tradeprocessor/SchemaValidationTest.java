package finos.traderx.tradeprocessor;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import finos.traderx.tradeprocessor.model.*;

class SchemaValidationTest {
    private Configuration mapping(String url) {
        return new Configuration().addAnnotatedClass(Trade.class).addAnnotatedClass(Position.class)
            .setProperty("hibernate.connection.url", url)
            .setProperty("hibernate.connection.username", "sa")
            .setProperty("hibernate.connection.password", "")
            .setProperty("hibernate.hbm2ddl.auto", "validate")
            .setProperty("hibernate.show_sql", "false");
    }

    @Test
    void D5_initialSchemaSupportsValidationAndTradePositionPersistence() throws Exception {
        String url = "jdbc:h2:mem:schema-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            Path schema = Path.of("../database/initialSchema.sql");
            if (!schema.toFile().exists()) schema = Path.of("database/initialSchema.sql");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(schema));
        }
        try (var factory = mapping(url).buildSessionFactory(); var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            Trade trade = new Trade();
            trade.setId(UUID.randomUUID().toString());
            trade.setAccountId(22214);
            trade.setSecurity("IBM");
            trade.setSide(TradeSide.Buy);
            trade.setQuantity(10);
            session.persist(trade);
            Position position = new Position();
            position.setAccountId(22214);
            position.setSecurity("D5TEST");
            position.setQuantity(10);
            session.persist(position);
            transaction.commit();
            session.clear();
            assertThat(session.find(Trade.class, trade.getId()).getQuantity()).isEqualTo(10);
            assertThat(session.find(Position.class, new PositionID(22214, "D5TEST")).getQuantity()).isEqualTo(10);
        }
    }

    @Test
    void D5_validationRejectsMissingSchemaWithoutCreatingTables() throws Exception {
        String url = "jdbc:h2:mem:missing-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        assertThatThrownBy(() -> mapping(url).buildSessionFactory()).hasMessageContaining("Schema-validation");
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var result = connection.createStatement().executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }
}
