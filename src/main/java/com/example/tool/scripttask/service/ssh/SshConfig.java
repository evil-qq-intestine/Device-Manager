package com.example.tool.scripttask.service.ssh;

/**
 * 纯 SSH 连接参数。私钥为明文，仅在内存中存在，用完即弃。
 */
public record SshConfig(String host, int port, String user, String privateKeyPem, String passphrase) {
}
