package com.example.tool.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 启动时检查 JWT 签名密钥是否仍是仓库里的占位值、为空或长度不足。
 * 占位密钥意味着任何拿到源码的人都能伪造登录 token，进而调用接口在受管主机上执行命令，
 * 所以默认大声告警；设置 {@code app.security.jwt.fail-on-insecure-secret=true} 可直接拒绝启动。
 * 日志用英文，避免 Windows GBK 控制台把中文打成乱码。
 */
@Slf4j
@Component
public class JwtSecretValidator implements ApplicationRunner {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "your-256-bit-secret-key-here-at-least-32-characters",
            "change-me", "changeme", "secret");

    private static final int MIN_BYTES = 32;

    private final String secret;
    private final boolean failOnInsecure;

    public JwtSecretValidator(@Value("${jwt.secret:}") String secret,
                              @Value("${app.security.jwt.fail-on-insecure-secret:true}") boolean failOnInsecure) {
        this.secret = secret;
        this.failOnInsecure = failOnInsecure;
    }

    @Override
    public void run(ApplicationArguments args) {
        String reason = insecureReason();
        if (reason == null) {
            return;
        }
        String advice = "Set the JWT_SECRET environment variable to a random value, e.g. `openssl rand -base64 48`. "
                + "Anyone who knows the current secret can forge a login token and run commands on managed hosts.";
        if (failOnInsecure) {
            throw new IllegalStateException("Insecure JWT signing secret (" + reason + "). " + advice
                    + " To start anyway (not recommended), set JWT_FAIL_ON_INSECURE=false.");
        }
        log.warn("========================================================================");
        log.warn("Insecure JWT signing secret ({}). {} Set JWT_FAIL_ON_INSECURE=true to make this fatal.",
                reason, advice);
        log.warn("========================================================================");
    }

    private String insecureReason() {
        if (secret == null || secret.isBlank()) {
            return "not configured";
        }
        if (PLACEHOLDERS.contains(secret.trim())) {
            return "still the repository placeholder";
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
            return "shorter than " + MIN_BYTES + " bytes";
        }
        return null;
    }
}
