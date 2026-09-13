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

    private final SshHostKeyService hostKeyService;

    public MinaSshExecutor(SshHostKeyService hostKeyService) {
        this.hostKeyService = hostKeyService;
    }

    @Override
    public SshResult executeScript(SshConfig config, ScriptType type, String script, long timeoutMs) {
        String command = (type == ScriptType.POWERSHELL)
                ? "powershell -NoProfile -NonInteractive -Command -"
                : "bash -s";
        return exec(config, command, script, timeoutMs);
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
        SshClient client = newClient(config);
        try {
            try (ClientSession session = authenticate(client, config, timeoutMs)) {
                return hostKeyService.findFingerprint(config.host(), config.port()).orElse("connected");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("SSH 连接失败: " + rootMessage(e), e);
        } finally {
            client.stop();
        }
    }

    private SshResult exec(SshConfig config, String command, String stdin, long timeoutMs) {
        SshClient client = newClient(config);
        try {
            try (ClientSession session = authenticate(client, config, timeoutMs)) {
                ClientChannel channel = session.createExecChannel(command);
                LimitedByteArrayOutputStream out = new LimitedByteArrayOutputStream(MAX_OUTPUT_BYTES);
                LimitedByteArrayOutputStream err = new LimitedByteArrayOutputStream(MAX_OUTPUT_BYTES);
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
            return SshResult.error("SSH 执行失败: " + rootMessage(e));
        } finally {
            client.stop();
        }
    }

    private SshClient newClient(SshConfig config) {
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(new FingerprintServerKeyVerifier(hostKeyService, config.host(), config.port()));
        client.start();
        return client;
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
                throw new BusinessException("私钥解析失败，请检查私钥内容");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("私钥格式错误或口令不正确: " + rootMessage(e), e);
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
