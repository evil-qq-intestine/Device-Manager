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
 */
@Slf4j
public class FingerprintServerKeyVerifier implements ServerKeyVerifier {

    private final SshHostKeyService hostKeyService;
    private final String host;
    private final int port;

    public FingerprintServerKeyVerifier(SshHostKeyService hostKeyService, String host, int port) {
        this.hostKeyService = hostKeyService;
        this.host = host;
        this.port = port;
    }

    @Override
    public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        try {
            String fingerprint = fingerprint(serverKey);
            return hostKeyService.verifyOrRegister(host, port, serverKey.getAlgorithm(), fingerprint);
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
