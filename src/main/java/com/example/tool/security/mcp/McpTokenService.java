package com.example.tool.security.mcp;

import com.example.tool.security.mcp.entity.McpJwtToken;
import com.example.tool.security.mcp.entity.McpTokenPermission;
import com.example.tool.security.mcp.response.CreateMcpJwtTokenResponse;
import com.example.tool.user.reopsitory.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpTokenService {

    private static final String TOKEN_PREFIX = "dm_";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final McpTokenRepository mcpTokenRepository;
    private final UserRepository userRepository;

    /**
     * 令牌校验：查摘要 → enabled → 过期 → 命中后回写 lastUsedAt / usedIp。
     *
     * <p>刻意用非只读事务：回写走 JPA 脏检查（实体在事务内是托管态，提交时自动 UPDATE，
     * 不需要 save()）。回写放在认证成功之后、工具执行之前，占用的时间极短，
     * 不会像 SSH 长 I/O 那样占着 SQLite 那唯一一条连接。
     *
     * <p>TODO 尚未实现：{@code allowedIp} 校验（目前该列只是展示用）。
     */
    @Transactional
    public McpTokenPrincipal findValid(String rawToken, String clientIp) {
        McpJwtToken t = mcpTokenRepository.findByTokenHash(sha256Hex(rawToken)).orElse(null);
        if (t == null || !Boolean.TRUE.equals(t.getIsEnabled())) return null;
        Instant now = Instant.now();
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(now)) return null;

        t.setLastUsedAt(now);
        t.setUsedIp(clientIp);

        return new McpTokenPrincipal(t.getId(), t.getUser().getId(), t.getTier(),
                Set.copyOf(t.getMcpTokenPermission()));
    }

    /**
     * 签发令牌：生成明文 {@code dm_xxx}，**只把 SHA-256 摘要入库**，明文仅在响应里返回这一次。
     *
     * <p>{@code name} / {@code expiresAt} / {@code allowedIp} 目前都留空（签名里没地方传），
     * 后续要暴露给管理端的话，把入参换成请求 DTO 即可。
     */
    @Transactional
    public CreateMcpJwtTokenResponse create(McpTokenPrincipal principal) {
        String rawToken = TOKEN_PREFIX + randomPart();
        McpJwtToken token = new McpJwtToken();
        token.setUser(userRepository.getReferenceById(principal.ownerId()));
        token.setTier(principal.tier());
        token.setMcpTokenPermission(Set.copyOf(principal.permissions()));
        token.setTokenHash(sha256Hex(rawToken));
        mcpTokenRepository.save(token);

        return new CreateMcpJwtTokenResponse(rawToken, scopesOf(token.getMcpTokenPermission()), token.getName());
    }

    private static String randomPart() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 按枚举声明顺序输出 scope，保证同一组权限每次 JSON 顺序一致（前端 diff / 测试断言都稳）。 */
    private static Set<String> scopesOf(Set<McpTokenPermission> permissions) {
        return Arrays.stream(McpTokenPermission.values())
                .filter(permissions::contains)
                .map(McpTokenPermission::scope)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
