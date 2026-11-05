package finos.traderx.database;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ClientRecordsTest {
    @Test
    void freshStartupInitializesThenMigratesBeforeServing() throws Exception {
        StartupResult result = startup(false, 0);
        assertEquals(0, result.exitCode());
        assertTrue(result.calls().contains("initialSchema.sql"));
        assertTrue(result.calls().contains("clientRecordsMigration.sql"));
        assertTrue(result.calls().indexOf("initialSchema.sql") < result.calls().indexOf("clientRecordsMigration.sql"));
        assertTrue(result.calls().indexOf("clientRecordsMigration.sql") < result.calls().indexOf("-jar"));
    }

    @Test
    void existingDatabaseIsNeverDestructivelyReinitializedOnStartup() throws Exception {
        StartupResult result = startup(true, 0);
        assertEquals(0, result.exitCode());
        assertFalse(result.calls().contains("initialSchema.sql"), "Startup must preserve existing trade history");
        assertTrue(result.calls().contains("clientRecordsMigration.sql"));
    }

    @Test
    void failedMigrationPreventsDatabaseServerStartup() throws Exception {
        StartupResult result = startup(true, 1);
        assertEquals(1, result.exitCode());
        assertFalse(result.calls().contains("-jar"));
    }

    private StartupResult startup(boolean existingDatabase, int migrationExit) throws Exception {
        Path directory = Files.createDirectories(Path.of("build", "fs09_startup_" + UUID.randomUUID())).toAbsolutePath();
        Path calls = directory.resolve("fs09-java-calls.log");
        Path java = directory.resolve("java");
        Files.writeString(java, """
                #!/bin/sh
                echo "$*" >> "$FS09_JAVA_CALLS"
                case "$*" in *clientRecordsMigration.sql*) exit "$FS09_MIGRATION_EXIT";; esac
                exit 0
                """);
        assertTrue(java.toFile().setExecutable(true));
        if (existingDatabase) Files.createFile(directory.resolve("fs09.mv.db"));
        ProcessBuilder process = new ProcessBuilder("bash", "run.sh");
        process.environment().put("PATH", directory + ":" + System.getenv("PATH"));
        process.environment().put("DATABASE_DATA_DIR", directory.toString());
        process.environment().put("DATABASE_DBNAME", "fs09");
        process.environment().put("FS09_JAVA_CALLS", calls.toString());
        process.environment().put("FS09_MIGRATION_EXIT", Integer.toString(migrationExit));
        process.redirectErrorStream(true).redirectOutput(directory.resolve("fs09-startup.log").toFile());
        Process child = process.start();
        boolean finished = child.waitFor(10, TimeUnit.SECONDS);
        if (!finished) child.destroyForcibly();
        assertTrue(finished, "Database startup script did not finish");
        return new StartupResult(child.exitValue(), Files.readString(calls));
    }

    private record StartupResult(int exitCode, String calls) {}

    @Test
    void bootstrapTradesHaveTheirInitialStatusRecordedInUtc() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:fs09_" + UUID.randomUUID());
             Statement sql = connection.createStatement();
             FileReader schema = new FileReader("initialSchema.sql")) {
            RunScript.execute(connection, schema);
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM TradeStatusHistory")) {
                rows.next();
                assertEquals(4, rows.getInt(1), "Bootstrap trade status must not be lost");
            }
            try (ResultSet rows = sql.executeQuery("SELECT ChangedAt FROM TradeStatusHistory")) {
                while (rows.next()) assertEquals(java.time.ZoneOffset.UTC, rows.getObject(1, java.time.OffsetDateTime.class).getOffset());
            }
        }
    }

    @Test
    void statusHistoryCannotBeOverwrittenOrDeleted() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:fs09_" + UUID.randomUUID());
             Statement sql = connection.createStatement();
             FileReader schema = new FileReader("initialSchema.sql")) {
            RunScript.execute(connection, schema);
            sql.execute("INSERT INTO TradeStatusHistory(TradeID, State, ChangedAt) VALUES ('TRADE-22214-AABBCC', 'Settled', TIMESTAMP WITH TIME ZONE '2026-11-05 12:00:00+00:00')");
            assertThrows(SQLException.class, () -> sql.executeUpdate("UPDATE TradeStatusHistory SET State='Cancelled'"), "Status history must be append-only");
            assertThrows(SQLException.class, () -> sql.executeUpdate("DELETE FROM TradeStatusHistory"));
        }
    }

    @Test
    void migrationPreservesHistoryAndStableAccountReferencesOnRepeatedStartup() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:fs09_" + UUID.randomUUID());
             Statement sql = connection.createStatement();
             FileReader schema = new FileReader("initialSchema.sql")) {
            RunScript.execute(connection, schema);
            sql.execute("INSERT INTO TradeStatusHistory(TradeID, State, ChangedAt) VALUES ('TRADE-22214-AABBCC', 'Settled', TIMESTAMP WITH TIME ZONE '2026-11-05 12:00:00+00:00')");
            String reference;
            try (ResultSet rows = sql.executeQuery("SELECT PublicReference FROM Accounts WHERE ID=22214")) {
                rows.next();
                reference = rows.getString(1);
            }
            for (int run = 0; run < 2; run++) {
                try (FileReader migration = new FileReader("clientRecordsMigration.sql")) {
                    RunScript.execute(connection, migration);
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT PublicReference FROM Accounts WHERE ID=22214")) {
                rows.next();
                assertEquals(reference, rows.getString(1));
            }
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM TradeStatusHistory")) {
                rows.next();
                assertEquals(5, rows.getInt(1));
            }
        }
    }

    @Test
    void legacyMigrationCreatesEmptyHistoryWithoutInventingTimestamps() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:fs09_" + UUID.randomUUID());
             Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE Accounts(ID INTEGER PRIMARY KEY, DisplayName VARCHAR(50))");
            sql.execute("CREATE TABLE Trades(ID VARCHAR(100) PRIMARY KEY)");
            sql.execute("INSERT INTO Accounts VALUES(22214, 'FS09')");
            try (FileReader migration = new FileReader("clientRecordsMigration.sql")) {
                RunScript.execute(connection, migration);
            }
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM TradeStatusHistory")) {
                rows.next();
                assertEquals(0, rows.getInt(1));
            }
        }
    }
}
