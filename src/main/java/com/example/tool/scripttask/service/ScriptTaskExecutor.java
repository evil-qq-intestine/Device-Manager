package com.example.tool.scripttask.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.service.WolService;
import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.repository.ScriptTaskLogRepository;
import com.example.tool.scripttask.repository.ScriptTaskRepository;
import com.example.tool.scripttask.service.component.CryptoService;
import com.example.tool.scripttask.service.ssh.SshConfig;
import com.example.tool.scripttask.service.ssh.SshExecutor;
import com.example.tool.scripttask.service.ssh.SshResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * 在单个目标设备上执行脚本：组装该设备的 SSH 配置 → 执行 → 记录 → 判断关机。
 * 独立 Bean，保证 {@link Async} 生效。
 */
@Slf4j
@Component
public class ScriptTaskExecutor {

    private static final int MAX_LAST_OUTPUT = 20000;

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskLogRepository logRepository;
    private final DeviceRepository deviceRepository;
    private final CryptoService cryptoService;
    private final SshExecutor sshExecutor;
    private final WolService wolService;

    @Value("${app.script.ssh.command-timeout-ms:120000}")
    private long commandTimeoutMs;

    public ScriptTaskExecutor(ScriptTaskRepository taskRepository,
                              ScriptTaskLogRepository logRepository,
                              DeviceRepository deviceRepository,
                              CryptoService cryptoService,
                              SshExecutor sshExecutor,
                              WolService wolService) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.deviceRepository = deviceRepository;
        this.cryptoService = cryptoService;
        this.sshExecutor = sshExecutor;
        this.wolService = wolService;
    }

    @Async("scriptTaskThreadPool")
    public void run(Long taskId, Long logId, Integer deviceId, TriggeredBy triggeredBy) {
        ScriptTaskLog logEntry = logRepository.findById(logId).orElse(null);
        if (logEntry == null) {
            log.warn("Log {} not found, skip execution of task {} on device {}", logId, taskId, deviceId);
            return;
        }
        ScriptTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            finish(logEntry, -1, null, null, "任务不存在", false);
            return;
        }
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            finish(logEntry, -1, null, null, "目标设备不存在", false);
            return;
        }
        if (device.getSshPrivateKeyEncrypted() == null) {
            finish(logEntry, -1, null, null, "目标设备未配置 SSH", false);
            updateTaskResult(task, -1, "目标设备未配置 SSH");
            return;
        }

        log.info("Executing script task {} on device {} (log {}, trigger {})", taskId, deviceId, logId, triggeredBy);
        try {
            SshConfig config = new SshConfig(
                    device.getSshHost(), device.getSshPort(), device.getSshUser(),
                    cryptoService.decrypt(device.getSshPrivateKeyEncrypted()),
                    cryptoService.decrypt(device.getSshKeyPassphraseEncrypted()));
            ScriptType type = device.getSshType() != null ? device.getSshType() : ScriptType.BASH;

            if (device.getStatus() != DeviceStatusEnum.ONLINE) {
                wakeAndWait(device, config);
            }

            SshResult result = sshExecutor.executeScript(config, type, task.getScriptContent(), commandTimeoutMs);

            boolean shutdown = false;
            if (result.success() && task.getShutdownMode() != ShutdownMode.NONE) {
                shutdown = triggerShutdown(task, device, config, type);
            }
            finish(logEntry, result.exitCode(), result.stdout(), result.stderr(), result.errorMessage(), shutdown);
            updateTaskResult(task, result.exitCode(), result.stdout());
        } catch (Exception e) {
            log.error("Script task {} execution error on device {}", taskId, deviceId, e);
            finish(logEntry, -1, null, null, e.getMessage(), false);
            updateTaskResult(task, -1, e.getMessage());
        }
    }

    /**
     * 目标非在线时先发唤醒魔术包，再轮询 SSH 端口，直到可达或设备 wakeTimeout 超时。
     * 超时抛异常，由 run() 统一记为执行失败；只探测端口，不重复执行脚本。
     */
    private void wakeAndWait(Device device, SshConfig config) {
        try {
            wolService.wake(device);
            log.info("Woke device {} before script execution", device.getDeviceId());
        } catch (Exception e) {
            log.warn("Wake device {} before script execution failed: {}", device.getDeviceId(), e.getMessage());
        }
        long timeoutSeconds = device.getWakeTimeout() != null ? device.getWakeTimeout() : 120;
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isReachable(config.host(), config.port())) {
                log.info("Device {} reachable, proceeding with script execution", device.getDeviceId());
                return;
            }
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IllegalStateException("设备未上线（等待 " + timeoutSeconds + " 秒超时）");
    }

    private boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean triggerShutdown(ScriptTask task, Device device, SshConfig config, ScriptType type) {
        boolean withPassword = type == ScriptType.BASH && device.getSudoPasswordEncrypted() != null;
        String command = ShutdownCommands.forTask(task, type, withPassword);
        String stdin = withPassword ? cryptoService.decrypt(device.getSudoPasswordEncrypted()) + "\n" : null;
        SshResult result = sshExecutor.executeCommand(config, command, stdin, commandTimeoutMs);
        if (!result.success()) {
            log.warn("Shutdown command failed for task {} on device {}: {}",
                    task.getId(), device.getDeviceId(), ShutdownCommands.describeFailure(result));
        }
        return result.success();
    }

    private void updateTaskResult(ScriptTask task, int exitCode, String output) {
        task.setLastExecutedAt(LocalDateTime.now());
        task.setLastExitCode(exitCode);
        task.setLastOutput(truncate(output));
        taskRepository.save(task);
    }

    private void finish(ScriptTaskLog logEntry, int exitCode, String stdout, String stderr,
                        String errorMessage, boolean shutdownTriggered) {
        logEntry.setExitCode(exitCode);
        logEntry.setStdout(stdout);
        logEntry.setStderr(stderr);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setShutdownTriggered(shutdownTriggered);
        logEntry.setFinishedAt(LocalDateTime.now());
        logRepository.save(logEntry);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_LAST_OUTPUT ? text : text.substring(0, MAX_LAST_OUTPUT) + "\n...[已截断]";
    }
}
