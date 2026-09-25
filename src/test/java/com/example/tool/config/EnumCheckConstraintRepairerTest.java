package com.example.tool.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumCheckConstraintRepairerTest {

    private static final String STALE_SCRIPT_TASK_DDL = """
            CREATE TABLE script_task (
                id integer,
                name varchar(255),
                enabled boolean not null check ((enabled in (0,1))),
                shutdown_mode varchar(255) not null check ((shutdown_mode in ('NONE','IMMEDIATE','DELAYED'))),
                trigger_type varchar(255) not null check ((trigger_type in ('ONCE','CRON','ON_BOOT'))),
                primary key (id)
            )""";

    private static final String CURRENT_SCRIPT_TASK_DDL = """
            CREATE TABLE script_task (
                id integer,
                name varchar(255),
                enabled boolean not null check ((enabled in (0,1))),
                shutdown_mode varchar(255) not null check ((shutdown_mode in ('NONE','IMMEDIATE','DELAYED'))),
                trigger_type varchar(255) not null check ((trigger_type in ('ONCE','CRON','ON_BOOT','MANUAL'))),
                primary key (id)
            )""";

    private static final Map<String, List<String>> EXPECTED = Map.of(
            "script_task.trigger_type", List.of("ONCE", "CRON", "ON_BOOT", "MANUAL"),
            "script_task.shutdown_mode", List.of("NONE", "IMMEDIATE", "DELAYED"));

    @TempDir
    Path tempDir;

    @Test
    void staleCheckRejectsNewValueBeforeRepair() throws SQLException {
        try (Connection connection = openDatabase(STALE_SCRIPT_TASK_DDL)) {
            assertThrows(SQLException.class, () -> insertScriptTask(connection, "MANUAL", "x"));
        }
    }

    @Test
    void acceptsNewValueAfterRepair() throws SQLException {
        try (Connection connection = openDatabase(STALE_SCRIPT_TASK_DDL)) {
            assertEquals(1, EnumCheckConstraintRepairer.repair(connection, EXPECTED));

            assertEquals(1, insertScriptTask(connection, "MANUAL", "x"));
            assertTrue(readCreateSql(connection).contains(
                    "check ((trigger_type in ('ONCE','CRON','ON_BOOT','MANUAL')))"));
        }
    }

    @Test
    void preservesExistingRows() throws SQLException {
        try (Connection connection = openDatabase(STALE_SCRIPT_TASK_DDL)) {
            insertScriptTask(connection, "ON_BOOT", "keep-me");

            assertEquals(1, EnumCheckConstraintRepairer.repair(connection, EXPECTED));

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT id, name, trigger_type FROM script_task")) {
                assertTrue(rs.next());
                assertEquals(7, rs.getInt("id"));
                assertEquals("keep-me", rs.getString("name"));
                assertEquals("ON_BOOT", rs.getString("trigger_type"));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void isIdempotent() throws SQLException {
        try (Connection connection = openDatabase(STALE_SCRIPT_TASK_DDL)) {
            assertEquals(1, EnumCheckConstraintRepairer.repair(connection, EXPECTED));
            String once = readCreateSql(connection);

            assertEquals(0, EnumCheckConstraintRepairer.repair(connection, EXPECTED));
            assertEquals(once, readCreateSql(connection));
        }
    }

    @Test
    void alreadyCurrentDatabaseIsUntouched() throws SQLException {
        try (Connection connection = openDatabase(CURRENT_SCRIPT_TASK_DDL)) {
            assertEquals(0, EnumCheckConstraintRepairer.repair(connection, EXPECTED));
            assertEquals(CURRENT_SCRIPT_TASK_DDL, readCreateSql(connection));
        }
    }

    @Test
    void leavesBooleanCheckAlone() throws SQLException {
        try (Connection connection = openDatabase(STALE_SCRIPT_TASK_DDL)) {
            EnumCheckConstraintRepairer.repair(connection, EXPECTED);
            assertTrue(readCreateSql(connection).contains("check ((enabled in (0,1)))"));
        }
    }

    @Test
    void leavesOrdinalChecksAlone() throws SQLException {
        String ddl = """
                CREATE TABLE sample (
                    id integer,
                    status integer check ((status in (0,1))),
                    primary key (id)
                )""";
        try (Connection connection = openDatabase(ddl)) {
            String updated = EnumCheckConstraintRepairer.repairTableSql(
                    readCreateSql(connection, "sample"), "sample", Map.of("sample.status", List.of("A", "B", "C")));
            assertNull(updated);
        }
    }

    @Test
    void repairTableSqlReturnsNullWhenAlreadyCurrent() {
        String sql = "CREATE TABLE t (trigger_type varchar(255) check ((trigger_type in ('ONCE','CRON'))))";
        assertNull(EnumCheckConstraintRepairer.repairTableSql(
                sql, "t", Map.of("t.trigger_type", List.of("ONCE", "CRON"))));
    }

    @Test
    void repairTableSqlReplacesOnlyTheValueList() {
        String sql = "CREATE TABLE t (id integer, trigger_type varchar(255) not null "
                + "check ((trigger_type in ('ONCE','CRON'))), primary key (id))";
        String updated = EnumCheckConstraintRepairer.repairTableSql(
                sql, "t", Map.of("t.trigger_type", List.of("ONCE", "CRON", "MANUAL")));
        assertEquals("CREATE TABLE t (id integer, trigger_type varchar(255) not null "
                + "check ((trigger_type in ('ONCE','CRON','MANUAL'))), primary key (id))", updated);
    }

    @Test
    void parserHandlesQuotedColumnNames() {
        String sql = "CREATE TABLE t (\"role\" varchar(255) check ((\"role\" in ('ADMIN'))))";
        String updated = EnumCheckConstraintRepairer.repairTableSql(
                sql, "t", Map.of("t.role", List.of("ADMIN", "USER")));
        assertEquals("CREATE TABLE t (\"role\" varchar(255) check ((\"role\" in ('ADMIN','USER'))))", updated);
    }

    private Connection openDatabase(String ddl) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve("data.db"));
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
        return connection;
    }

    private static int insertScriptTask(Connection connection, String triggerType, String name) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("INSERT INTO script_task (id, name, enabled, shutdown_mode, trigger_type) "
                    + "VALUES (7, '" + name + "', 1, 'NONE', '" + triggerType + "')");
        }
    }

    private static String readCreateSql(Connection connection) throws SQLException {
        return readCreateSql(connection, "script_task");
    }

    private static String readCreateSql(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
