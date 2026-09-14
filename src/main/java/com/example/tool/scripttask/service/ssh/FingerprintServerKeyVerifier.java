package com.example.tool.scripttask.service.ssh;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;

/**
 * 基于指纹校验的目标主机公钥验证器。绑定固定的 host:port。
 *
 * <p>已知指纹在连接前由调用线程预先加载，验证过程本身不访问数据库：
 * 该回调运行在 MINA 的 I/O 线程上，而调用线程可能正持有数据库连接
 * （SQLite 连接池大小为 1），若在此处查询数据库会造成死锁。</p>
 */
@Slf4j
public class FingerprintServerKeyVerifier implements ServerKeyVerifier {

    private final String host;
    private final int port;
    private final String knownFingerprint;

    private volatile String observedKeyType;
    private volatile String observedFingerprint;

    public FingerprintServerKeyVerifier(String host, int port, String knownFingerprint) {
        this.host = host;
        this.port = port;
        this.knownFingerprint = knownFingerprint;
    }

    public String knownFingerprint() {
        return knownFingerprint;
    }

    public String observedKeyType() {
        return observedKeyType;
    }

    public String observedFingerprint() {
        return observedFingerprint;
    }

    @Override
    public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        try {
            observedKeyType = serverKey.getAlgorithm();
            observedFingerprint = fingerprint(serverKey);
            if (knownFingerprint == null) {
                // 首次连接：先信任，连接成功后由调用方记录指纹
                return true;
            }
            boolean match = knownFingerprint.equals(observedFingerprint);
            if (!match) {
                log.warn("SSH host key mismatch for {}:{} (expected {}, got {})",
                        host, port, knownFingerprint, observedFingerprint);
            }
            return match;
        } catch (Exception e) {
            log.error("Failed to verify SSH host key for {}:{}", host, port, e);
            return false;
        }
    }

    static String fingerprint(PublicKey key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getEncoded());
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hash);
    }
}
