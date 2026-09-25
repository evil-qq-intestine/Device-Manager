package com.example.tool.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterKeyLocationValidatorTest {

    @Test
    void parsesSqliteFilePath() {
        assertEquals(Path.of("./data.db"), MasterKeyLocationValidator.sqliteFile("jdbc:sqlite:./data.db"));
        assertEquals(Path.of("/app/data/data.db"), MasterKeyLocationValidator.sqliteFile("jdbc:sqlite:/app/data/data.db"));
    }

    @Test
    void stripsQueryParameters() {
        assertEquals(Path.of("./data.db"), MasterKeyLocationValidator.sqliteFile("jdbc:sqlite:./data.db?busy_timeout=5000"));
    }

    @Test
    void ignoresMemoryAndNonSqliteUrls() {
        assertNull(MasterKeyLocationValidator.sqliteFile("jdbc:sqlite::memory:"));
        assertNull(MasterKeyLocationValidator.sqliteFile("jdbc:postgresql://localhost/db"));
        assertNull(MasterKeyLocationValidator.sqliteFile(null));
    }

    @Test
    void detectsSameDirectory() {
        assertTrue(MasterKeyLocationValidator.sameDirectory(Path.of("./data.db"), Path.of("./.master-key")));
        assertFalse(MasterKeyLocationValidator.sameDirectory(Path.of("./data.db"), Path.of("/etc/secret/master-key")));
        assertFalse(MasterKeyLocationValidator.sameDirectory(null, Path.of("./.master-key")));
    }
}
