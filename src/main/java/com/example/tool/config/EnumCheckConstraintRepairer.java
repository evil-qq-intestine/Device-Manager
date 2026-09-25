package com.example.tool.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQLite has no {@code ALTER TABLE ... DROP/ALTER CONSTRAINT}, and Hibernate's
 * {@code ddl-auto=update} only adds tables and columns — it never rewrites an existing
 * CHECK constraint. So when a Java enum gains a new constant (e.g. {@code TriggerType.MANUAL}),
 * databases created by an older build keep a stale CHECK that rejects the new value and
 * surfaces as an HTTP 500 {@code SQLITE_CONSTRAINT_CHECK}.
 *
 * <p>This runner heals such databases on startup: for every mapped enum property it compares
 * the existing string CHECK against the current enum constants and, when they differ, rebuilds
 * the table with only the CHECK text replaced. The original {@code CREATE TABLE} statement read
 * from {@code sqlite_master} is reused verbatim, so column types, defaults and primary keys are
 * untouched; indexes/foreign keys are unaffected (this schema has none).
 *
 * <p>Runs after the schema has been created/updated by Hibernate. It is idempotent (a second run
 * is a no-op) and never aborts startup — a failure is logged so the operator can investigate.
 */
@Slf4j
@Component
public class EnumCheckConstraintRepairer implements ApplicationRunner {

    private static final Pattern ENUM_CHECK = Pattern.compile(
            "check\\s*\\(\\s*\\(\\s*"
                    + "([\"'`\\[][A-Za-z_][A-Za-z0-9_]*[\"'`\\]]|[A-Za-z_][A-Za-z0-9_]*)"
                    + "\\s+in\\s*\\(([^()]*)\\)\\s*\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_VALUE = Pattern.compile("'([^']*)'");
    private static final String TEMP_SUFFIX = "__dm_repair_tmp";

    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    public EnumCheckConstraintRepairer(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, List<String>> expected;
        try {
            expected = expectedEnumChecks();
        } catch (RuntimeException ex) {
            log.error("Could not determine the expected enum CHECK constraints; skipping self-repair", ex);
            return;
        }
        if (expected.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            int repaired = repair(connection, expected);
            if (repaired > 0) {
                log.info("Repaired {} stale enum CHECK constraint(s) so newly added enum values are accepted again",
                        repaired);
            } else {
                log.debug("Enum CHECK constraints are up to date");
            }
        } catch (Exception ex) {
            log.error("Failed to self-repair stale enum CHECK constraints. A stale constraint can reject valid "
                    + "values and surface as HTTP 500; the issue resolves automatically once repaired.", ex);
        }
    }

    private Map<String, List<String>> expectedEnumChecks() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        Map<String, List<String>> expected = new LinkedHashMap<>();
        sessionFactory.getMappingMetamodel().forEachEntityDescriptor(persister -> collect(persister, expected));
        return expected;
    }

    private static void collect(EntityPersister persister, Map<String, List<String>> expected) {
        String table = unquote(persister.getTableName());
        for (String property : persister.getPropertyNames()) {
            Type type = persister.getPropertyType(property);
            Class<?> javaType = type.getReturnedClass();
            if (javaType == null || !javaType.isEnum()) {
                continue;
            }
            Object[] constants = javaType.getEnumConstants();
            if (constants == null || constants.length == 0) {
                continue;
            }
            String[] columns = persister.getPropertyColumnNames(property);
            if (columns == null || columns.length != 1) {
                continue;
            }
            List<String> values = new ArrayList<>(constants.length);
            for (Object constant : constants) {
                values.add(((Enum<?>) constant).name());
            }
            expected.put(key(table, unquote(columns[0])), values);
        }
    }

    /**
     * Repairs every table whose string enum CHECK no longer matches the current enum constants.
     *
     * @return the number of tables rebuilt
     */
    static int repair(Connection connection, Map<String, List<String>> expected) throws SQLException {
        List<TableDefinition> tables = readTables(connection);
        int repaired = 0;
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (TableDefinition table : tables) {
                String updated = repairTableSql(table.createSql(), table.name(), expected);
                if (updated == null) {
                    continue;
                }
                rebuild(connection, table.name(), updated);
                repaired++;
                log.info("Repaired CHECK constraint on table '{}'", table.name());
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        return repaired;
    }

    private static List<TableDefinition> readTables(Connection connection) throws SQLException {
        List<TableDefinition> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND sql IS NOT NULL")) {
            while (rs.next()) {
                String name = rs.getString(1);
                String sql = rs.getString(2);
                if (name == null || sql == null || name.startsWith("sqlite_") || name.endsWith(TEMP_SUFFIX)) {
                    continue;
                }
                tables.add(new TableDefinition(name, sql));
            }
        }
        return tables;
    }

    /**
     * Returns the given {@code CREATE TABLE} statement with stale enum CHECK value lists replaced,
     * or {@code null} when nothing needs to change.
     */
    static String repairTableSql(String createSql, String tableName, Map<String, List<String>> expected) {
        Matcher matcher = ENUM_CHECK.matcher(createSql);
        StringBuilder result = new StringBuilder();
        int last = 0;
        boolean changed = false;
        while (matcher.find()) {
            List<String> wanted = expected.get(key(tableName, matcher.group(1)));
            if (wanted == null) {
                continue;
            }
            List<String> actual = quotedValues(matcher.group(2));
            if (actual == null || actual.equals(wanted)) {
                continue;
            }
            result.append(createSql, last, matcher.start());
            result.append("check ((").append(matcher.group(1)).append(" in (").append(render(wanted)).append(")))");
            last = matcher.end();
            changed = true;
        }
        if (!changed) {
            return null;
        }
        result.append(createSql, last, createSql.length());
        return result.toString();
    }

    private static void rebuild(Connection connection, String tableName, String createSql) throws SQLException {
        String quoted = quote(tableName);
        String temporary = quote(tableName + TEMP_SUFFIX);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + quoted + " RENAME TO " + temporary);
            statement.executeUpdate(createSql);
            statement.executeUpdate("INSERT INTO " + quoted + " SELECT * FROM " + temporary);
            statement.executeUpdate("DROP TABLE " + temporary);
        }
    }

    /** Parses {@code 'A','B'} into {@code [A, B]}, or returns {@code null} if any item is not a quoted string. */
    private static List<String> quotedValues(String list) {
        List<String> values = new ArrayList<>();
        for (String raw : list.split(",")) {
            Matcher matcher = QUOTED_VALUE.matcher(raw.trim());
            if (!matcher.matches()) {
                return null;
            }
            values.add(matcher.group(1));
        }
        return values;
    }

    private static String render(List<String> values) {
        return values.stream().map(v -> "'" + v.replace("'", "''") + "'").collect(Collectors.joining(","));
    }

    private static String key(String table, String column) {
        return unquote(table).toLowerCase(Locale.ROOT) + "." + unquote(column).toLowerCase(Locale.ROOT);
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String unquote(String identifier) {
        if (identifier == null || identifier.length() < 2) {
            return identifier;
        }
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '"' && last == '"') || (first == '`' && last == '`')
                || (first == '[' && last == ']')) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }

    private record TableDefinition(String name, String createSql) {
    }
}
