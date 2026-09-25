package com.example.tool.scripttask.service.ssh;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.scripttask.entity.ScriptType;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceParser;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Apache MINA SSHD 的实现。仅支持公钥认证。
 */
@Slf4j
@Component
public class MinaSshExecutor implements SshExecutor {

    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;

    /**
     * PowerShell 引导脚本：用 UTF-8 从 stdin 读取用户脚本再执行，并把输出编码强制为 UTF-8，
     * 避免 Windows PowerShell 5.1 默认按 CP936/GBK 读写导致中文乱码。
     * 仅含 ASCII 与单引号，且不含 {@code " & | < > %} 等会被 {@code cmd.exe /c} 解释的字符。
     */
    private static final String POWERSHELL_BOOTSTRAP =
            "try{[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)}catch{};"
                    + "$r=New-Object IO.StreamReader([Console]::OpenStandardInput(),"
                    + "[Text.UTF8Encoding]::new($false));$src=$r.ReadToEnd();"
                    + "if($src.Length -gt 0){iex $src}";

    private final SshHostKeyService hostKeyService;
    private final OutputCharset outputCharset;

    public MinaSshExecutor(SshHostKeyService hostKeyService,
                           @Value("${app.script.ssh.output-charset:AUTO}") String outputCharset) {
        this.hostKeyService = hostKeyService;
        this.outputCharset = OutputCharset.fromConfig(outputCharset);
    }

    static String scriptCommand(ScriptType type) {
        return (type == ScriptType.POWERSHELL)
                ? "powershell -NoProfile -NonInteractive -Command " + POWERSHELL_BOOTSTRAP
                : "bash -s";
    }

    @Override
    public SshResult executeScript(SshConfig config, ScriptType type, String script, long timeoutMs) {
        return exec(config, scriptCommand(type), script, timeoutMs);
    }

    @Override
    public SshResult executeCommand(SshConfig config, String command, long timeoutMs) {
        return exec(config, command, null, timeoutMs);
    }

    @Override
    public SshResult executeCommand(SshConfig config, String command, String stdin, long timeoutMs) {
        return exec(config, command, stdin, timeoutMs);
    }

    @Override
    public String testConnection(SshConfig config, long timeoutMs) {
        ClientHandle handle = newClient(config);
        try {
            try (ClientSession session = authenticate(handle.client(), config, timeoutMs)) {
                recordHostKeyIfNew(config, handle.verifier());
                return hostKeyService.findFingerprint(config.host(), config.port()).orElse("connected");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SSH connection test failed for {}:{} - {}", config.host(), config.port(), rootMessage(e));
            throw new BusinessException("SSH 连接失败，请检查主机、端口与网络连通性", e);
        } finally {
            handle.client().stop();
        }
    }

    private SshResult exec(SshConfig config, String command, String stdin, long timeoutMs) {
        ClientHandle handle = newClient(config);
        try {
            try (ClientSession session = authenticate(handle.client(), config, timeoutMs)) {
                recordHostKeyIfNew(config, handle.verifier());
                ClientChannel channel = session.createExecChannel(command);
                LimitedByteArrayOutputStream out = new LimitedByteArrayOutputStream(MAX_OUTPUT_BYTES, outputCharset);
                LimitedByteArrayOutputStream err = new LimitedByteArrayOutputStream(MAX_OUTPUT_BYTES, outputCharset);
                channel.setOut(out);
                channel.setErr(err);
                byte[] input = stdin != null ? stdin.getBytes(StandardCharsets.UTF_8) : new byte[0];
                channel.setIn(new ByteArrayInputStream(input));

                channel.open().verify(timeoutMs);
                Set<ClientChannelEvent> events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), timeoutMs);
                if (events.contains(ClientChannelEvent.TIMEOUT)) {
                    channel.close(true);
                    return new SshResult(-1, out.asString(), err.asString(), "执行超时");
                }
                Integer exit = channel.getExitStatus();
                return new SshResult(exit != null ? exit : -1, out.asString(), err.asString(), null);
            }
        } catch (BusinessException e) {
            return SshResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("SSH execution failed for {}:{} - {}", config.host(), config.port(), rootMessage(e));
            return SshResult.error("SSH 执行失败，请查看服务端日志了解详情");
        } finally {
            handle.client().stop();
        }
    }

    private ClientHandle newClient(SshConfig config) {
        String knownFingerprint = hostKeyService.findFingerprint(config.host(), config.port()).orElse(null);
        FingerprintServerKeyVerifier verifier =
                new FingerprintServerKeyVerifier(config.host(), config.port(), knownFingerprint);
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(verifier);
        client.start();
        return new ClientHandle(client, verifier);
    }

    private void recordHostKeyIfNew(SshConfig config, FingerprintServerKeyVerifier verifier) {
        if (verifier.knownFingerprint() == null && verifier.observedFingerprint() != null) {
            hostKeyService.verifyOrRegister(config.host(), config.port(),
                    verifier.observedKeyType(), verifier.observedFingerprint());
        }
    }

    private record ClientHandle(SshClient client, FingerprintServerKeyVerifier verifier) {
    }

    private ClientSession authenticate(SshClient client, SshConfig config, long timeoutMs) throws Exception {
        List<KeyPair> keyPairs = loadKeyPairs(config);
        ClientSession session = client.connect(config.user(), config.host(), config.port()).verify(timeoutMs).getSession();
        for (KeyPair keyPair : keyPairs) {
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(timeoutMs);
        return session;
    }

    private List<KeyPair> loadKeyPairs(SshConfig config) {
        KeyPairResourceParser parser = SecurityUtils.getKeyPairResourceParser();
        FilePasswordProvider passwordProvider =
                (config.passphrase() == null || config.passphrase().isEmpty())
                        ? FilePasswordProvider.EMPTY
                        : (session, resourceKey, retryIndex) -> config.passphrase();
        try (InputStream in = new ByteArrayInputStream(config.privateKeyPem().getBytes(StandardCharsets.UTF_8))) {
            NamedResource resource = () -> "private-key";
            Iterable<KeyPair> pairs = parser.loadKeyPairs(null, resource, passwordProvider, in);
            List<KeyPair> result = new ArrayList<>();
            pairs.forEach(result::add);
            if (result.isEmpty()) {
                throw new BusinessException("私钥解析失败：请确认是 OpenSSH/PEM 格式私钥（不支持 PuTTY .ppk）");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse private key for {}:{} - {}",
                    config.host(), config.port(), rootMessage(e));
            throw new BusinessException("私钥格式错误或口令不正确", e);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
