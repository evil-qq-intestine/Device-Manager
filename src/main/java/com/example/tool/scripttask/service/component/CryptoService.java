package com.example.tool.scripttask.service.component;

import com.example.tool.device.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 私钥加解密的唯一入口。AES-256-GCM，结构：[12B IV][密文][16B Tag] → Base64。
 * 主密钥优先取环境变量 APP_MASTER_KEY（Base64，32 字节），否则读取/生成本地密钥文件。
 */
@Slf4j
@Service
public class CryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey masterKey;

    @Autowired
    public CryptoService(@Value("${app.script.master-key-file:./.master-key}") String keyFile) {
        this.masterKey = new SecretKeySpec(resolveKey(keyFile), "AES");
    }

    private CryptoService(byte[] key) {
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    /** 仅用于测试。 */
    static CryptoService withKey(byte[] key) {
        return new CryptoService(key);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException("私钥加密失败", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("cipher too short");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("私钥解密失败，请重新上传私钥", e);
        }
    }

    private byte[] resolveKey(String keyFile) {
        String envKey = System.getenv("APP_MASTER_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return decodeKey(envKey.trim(), "APP_MASTER_KEY");
        }

        Path path = Path.of(keyFile);
        try {
            if (Files.exists(path)) {
                return decodeKey(Files.readString(path).trim(), keyFile);
            }
            byte[] generated = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            Files.writeString(path, Base64.getEncoder().encodeToString(generated));
            restrictPermissions(path);
            log.warn("Master key not found, generated a new one at {} (keep it secret, do not commit)", path.toAbsolutePath());
            return generated;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取/生成主密钥失败: " + keyFile, e);
        }
    }

    private byte[] decodeKey(String base64, String source) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("主密钥不是合法的 Base64: " + source);
        }
        if (key.length != KEY_BYTES) {
            throw new BusinessException("主密钥长度必须是 32 字节(AES-256)，当前: " + key.length + " (" + source + ")");
        }
        return key;
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // Windows 等不支持 POSIX 权限，忽略
        }
    }
}
