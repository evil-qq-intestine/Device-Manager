package com.example.tool.scripttask.service.ssh;

import com.example.tool.scripttask.entity.SshHostKey;
import com.example.tool.scripttask.repository.SshHostKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 目标主机公钥指纹的存取与校验：首次连接记录，之后必须匹配。
 */
@Slf4j
@Service
public class SshHostKeyService {

    private final SshHostKeyRepository repository;

    public SshHostKeyService(SshHostKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * @return true 表示指纹匹配（或首次记录成功）
     */
    @Transactional
    public boolean verifyOrRegister(String host, Integer port, String keyType, String fingerprint) {
        Optional<SshHostKey> existing = repository.findByHostAndPort(host, port);
        if (existing.isPresent()) {
            boolean match = existing.get().getFingerprintSha256().equals(fingerprint);
            if (!match) {
                log.warn("SSH host key mismatch for {}:{} (expected {}, got {})",
                        host, port, existing.get().getFingerprintSha256(), fingerprint);
            }
            return match;
        }
        try {
            repository.save(SshHostKey.builder()
                    .host(host)
                    .port(port)
                    .keyType(keyType)
                    .fingerprintSha256(fingerprint)
                    .build());
            log.info("Recorded SSH host key fingerprint for {}:{} -> {}", host, port, fingerprint);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 并发首次连接，重新读取比对
            return repository.findByHostAndPort(host, port)
                    .map(k -> k.getFingerprintSha256().equals(fingerprint))
                    .orElse(false);
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> findFingerprint(String host, Integer port) {
        return repository.findByHostAndPort(host, port).map(SshHostKey::getFingerprintSha256);
    }
}
