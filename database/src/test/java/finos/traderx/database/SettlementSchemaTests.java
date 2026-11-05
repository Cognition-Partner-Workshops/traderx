package finos.traderx.database;

import java.io.FileReader;
import java.sql.*;
import java.time.LocalDate;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SettlementSchemaTests {
    private Connection connection;

    @BeforeEach
    void loadSchema() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:schema-test");
        try (FileReader script = new FileReader("initialSchema.sql")) {
            RunScript.execute(connection, script);
        }
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    @Test
    void seedsHistoricalSettledTradesWithDates() throws Exception {
        try (var rows = connection.createStatement().executeQuery("SELECT Created, Updated, SettlementDate, State FROM Trades")) {
            int count = 0;
            while (rows.next()) {
                count++;
                assertEquals(LocalDate.parse("2024-01-09"), rows.getObject("SettlementDate", LocalDate.class));
                assertEquals("Settled", rows.getString("State"));
                assertTrue(rows.getTimestamp("Created").before(rows.getTimestamp("Updated")));
            }
            assertEquals(4, count);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-03", "2026-11-06", "2026-11-09"})
    void persistsDateWithoutTimeAndRetainsItWhenSettling(String date) throws Exception {
        try (var insert = connection.prepareStatement("INSERT INTO Trades(ID, AccountID, SettlementDate, Security, Side, Quantity, State) VALUES('new-trade',22214,?,'IBM','Buy',10,'Processing')")) {
            insert.setObject(1, LocalDate.parse(date));
            insert.executeUpdate();
        }
        connection.createStatement().executeUpdate("UPDATE Trades SET State='Settled' WHERE ID='new-trade'");
        try (var rows = connection.createStatement().executeQuery("SELECT SettlementDate, State FROM Trades WHERE ID='new-trade'")) {
            assertTrue(rows.next());
            assertEquals(LocalDate.parse(date), rows.getObject(1, LocalDate.class));
            assertEquals("Settled", rows.getString(2));
        }
    }

    @Test
    void rejectsMissingSettlementDate() {
        assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate(
            "INSERT INTO Trades(ID, AccountID, Quantity, State) VALUES('missing',22214,10,'Processing')"));
    }
}
