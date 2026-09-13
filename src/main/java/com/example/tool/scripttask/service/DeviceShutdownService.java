package com.example.tool.scripttask.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.request.DeviceSshRequest;
import com.example.tool.scripttask.response.DeviceSshResponse;
import com.example.tool.scripttask.service.component.CryptoService;
import com.example.tool.scripttask.service.ssh.SshConfig;
import com.example.tool.scripttask.service.ssh.SshExecutor;
import com.example.tool.scripttask.service.ssh.SshResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备级 SSH 配置与独立关机，不依赖任何脚本任务。
 */
@Service
public class DeviceShutdownService {

    private final DeviceRepository deviceRepository;
    private final CryptoService cryptoService;
    private final SshExecutor sshExecutor;

    @Value("${app.script.ssh.command-timeout-ms:120000}")
    private long commandTimeoutMs;

    public DeviceShutdownService(DeviceRepository deviceRepository,
                                 CryptoService cryptoService,
                                 SshExecutor sshExecutor) {
        this.deviceRepository = deviceRepository;
        this.cryptoService = cryptoService;
        this.sshExecutor = sshExecutor;
    }

    @Transactional(readOnly = true)
    public DeviceSshResponse getSshConfig(Integer deviceId, Integer userId) {
        return DeviceSshResponse.from(requireOwned(deviceId, userId));
    }

    @Transactional
    public DeviceSshResponse setSshConfig(Integer deviceId, Integer userId, DeviceSshRequest request) {
        Device device = requireOwned(deviceId, userId);
        if (device.getSshPrivateKeyEncrypted() == null && isBlank(request.getSshPrivateKey())) {
            throw new BusinessException("首次配置必须上传 SSH 私钥");
        }

        device.setSshHost(request.getSshHost());
        device.setSshPort(request.getSshPort());
        device.setSshUser(request.getSshUser());
        if (request.getSshType() != null) {
            device.setSshType(request.getSshType());
        }
        if (!isBlank(request.getSshPrivateKey())) {
            device.setSshPrivateKeyEncrypted(cryptoService.encrypt(request.getSshPrivateKey()));
        }
        if (request.getSshKeyPassphrase() != null) {
            device.setSshKeyPassphraseEncrypted(cryptoService.encrypt(blankToNull(request.getSshKeyPassphrase())));
        }
        if (request.getSudoPassword() != null) {
            device.setSudoPasswordEncrypted(cryptoService.encrypt(blankToNull(request.getSudoPassword())));
        }
        return DeviceSshResponse.from(deviceRepository.save(device));
    }

    /**
     * 立即关机（忽略任何脚本任务）。
     */
    @Transactional
    public Map<String, Object> shutdownNow(Integer deviceId, Integer userId) {
        Device device = requireOwned(deviceId, userId);
        if (device.getSshPrivateKeyEncrypted() == null) {
            throw new BusinessException("设备未配置 SSH，无法关机");
        }

        boolean withPassword = device.getSudoPasswordEncrypted() != null;
        ScriptType type = device.getSshType() != null ? device.getSshType() : ScriptType.BASH;

        SshResult result;
        try {
            SshConfig config = new SshConfig(
                    device.getSshHost(), device.getSshPort(), device.getSshUser(),
                    cryptoService.decrypt(device.getSshPrivateKeyEncrypted()),
                    cryptoService.decrypt(device.getSshKeyPassphraseEncrypted()));
            String command = ShutdownCommands.immediate(type, withPassword);
            String stdin = withPassword ? cryptoService.decrypt(device.getSudoPasswordEncrypted()) + "\n" : null;
            result = sshExecutor.executeCommand(config, command, stdin, commandTimeoutMs);
        } catch (Exception e) {
            result = SshResult.error(e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shutdownTriggered", result.success());
        response.put("exitCode", result.exitCode());
        response.put("errorMessage", result.success() ? null : ShutdownCommands.describeFailure(result));
        return response;
    }

    private Device requireOwned(Integer deviceId, Integer userId) {
        return deviceRepository.findByDeviceIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new AccessDeniedException("Device not found or access denied"));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
