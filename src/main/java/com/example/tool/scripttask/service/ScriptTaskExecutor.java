package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
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

import java.time.LocalDateTime;

/**
 * 执行单个任务：组装 SSH 配置 → 执行 → 记录 → 判断关机。
 * 独立 Bean，保证 {@link Async} 生效。
 */
@Slf4j
@Component
public class ScriptTaskExecutor {

    private static final int MAX_LAST_OUTPUT = 20000;

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskLogRepository logRepository;
    private final CryptoService cryptoService;
    private final SshExecutor sshExecutor;

    @Value("${app.script.ssh.command-timeout-ms:120000}")
    private long commandTimeoutMs;

    public ScriptTaskExecutor(ScriptTaskRepository taskRepository,
                              ScriptTaskLogRepository logRepository,
                              CryptoService cryptoService,
                              SshExecutor sshExecutor) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.cryptoService = cryptoService;
        this.sshExecutor = sshExecutor;
    }

    @Async("scriptTaskThreadPool")
    public void run(Long taskId, Long logId, TriggeredBy triggeredBy) {
        ScriptTaskLog logEntry = logRepository.findById(logId).orElse(null);
        if (logEntry == null) {
            log.warn("Log {} not found, skip execution of task {}", logId, taskId);
            return;
        }
        ScriptTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            finish(logEntry, -1, null, null, "任务不存在", false);
            return;
        }

        log.info("Executing script task {} (log {}, trigger {})", taskId, logId, triggeredBy);
        try {
            SshConfig config = new SshConfig(
                    task.getSshHost(), task.getSshPort(), task.getSshUser(),
                    cryptoService.decrypt(task.getSshPrivateKeyEncrypted()),
                    cryptoService.decrypt(task.getSshKeyPassphraseEncrypted()));

            SshResult result = sshExecutor.executeScript(config, task.getScriptType(), task.getScriptContent(), commandTimeoutMs);

            boolean shutdown = false;
            if (result.success() && task.getShutdownMode() != ShutdownMode.NONE) {
                shutdown = triggerShutdown(task, config);
            }
            finish(logEntry, result.exitCode(), result.stdout(), result.stderr(), result.errorMessage(), shutdown);

            task.setLastExecutedAt(LocalDateTime.now());
            task.setLastExitCode(result.exitCode());
            task.setLastOutput(truncate(result.stdout()));
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("Script task {} execution error", taskId, e);
            finish(logEntry, -1, null, null, e.getMessage(), false);
            task.setLastExecutedAt(LocalDateTime.now());
            task.setLastExitCode(-1);
            task.setLastOutput(truncate(e.getMessage()));
            taskRepository.save(task);
        }
    }

    private boolean triggerShutdown(ScriptTask task, SshConfig config) {
        boolean withPassword = task.getSudoPasswordEncrypted() != null;
        String command = ShutdownCommands.forTask(task, withPassword);
        String stdin = withPassword ? cryptoService.decrypt(task.getSudoPasswordEncrypted()) + "\n" : null;
        SshResult result = sshExecutor.executeCommand(config, command, stdin, commandTimeoutMs);
        if (!result.success()) {
            log.warn("Shutdown command failed for task {}: {}", task.getId(), ShutdownCommands.describeFailure(result));
        }
        return result.success();
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
