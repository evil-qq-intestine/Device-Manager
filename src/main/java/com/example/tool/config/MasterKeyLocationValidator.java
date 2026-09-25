package com.example.tool.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 启动时检查主密钥文件是否与 SQLite 数据库放在同一目录。
 * 两者同目录时，任何拿到该目录的方式（备份、镜像层、误拷）都会同时拿到密文和密钥，
 * 静态加密就形同虚设。日志用英文，避免 Windows GBK 控制台把中文打成乱码。
 */
@Slf4j
@Component
public class MasterKeyLocationValidator implements ApplicationRunner {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private final String datasourceUrl;
    private final String masterKeyFile;

    public MasterKeyLocationValidator(@Value("${spring.datasource.url}") String datasourceUrl,
                                      @Value("${app.script.master-key-file:./.master-key}") String masterKeyFile) {
        this.datasourceUrl = datasourceUrl;
        this.masterKeyFile = masterKeyFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        String envKey = System.getenv("APP_MASTER_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return;
        }
        if (masterKeyFile == null || masterKeyFile.isBlank()) {
            return;
        }
        Path db = sqliteFile(datasourceUrl);
        Path key = Path.of(masterKeyFile);
        if (!sameDirectory(db, key)) {
            return;
        }
        log.warn("========================================================================");
        log.warn("Master key file {} and the SQLite database {} are in the same directory {}. "
                        + "Anyone who obtains that directory (backup, image layer, accidental copy) gets both the "
                        + "ciphertext and the key, which defeats encryption at rest. Prefer passing APP_MASTER_KEY "
                        + "from a separate secret store, or move the key file elsewhere.",
                key.toAbsolutePath().normalize(), db.toAbsolutePath().normalize(),
                key.toAbsolutePath().normalize().getParent());
        log.warn("========================================================================");
    }

    static Path sqliteFile(String datasourceUrl) {
        if (datasourceUrl == null || !datasourceUrl.startsWith(SQLITE_PREFIX)) {
            return null;
        }
        String path = datasourceUrl.substring(SQLITE_PREFIX.length());
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.isBlank() || ":memory:".equals(path)) {
            return null;
        }
        return Path.of(path);
    }

    static boolean sameDirectory(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        Path parentA = a.toAbsolutePath().normalize().getParent();
        Path parentB = b.toAbsolutePath().normalize().getParent();
        return parentA != null && parentA.equals(parentB);
    }
}
