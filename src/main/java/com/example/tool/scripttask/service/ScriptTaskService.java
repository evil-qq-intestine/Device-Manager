package com.example.tool.scripttask.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.repository.ScriptTaskLogRepository;
import com.example.tool.scripttask.repository.ScriptTaskRepository;
import com.example.tool.scripttask.request.ScriptTaskRequest;
import com.example.tool.scripttask.request.TestSshRequest;
import com.example.tool.scripttask.response.PageResponse;
import com.example.tool.scripttask.response.ScriptTaskLogDetailResponse;
import com.example.tool.scripttask.response.ScriptTaskLogResponse;
import com.example.tool.scripttask.response.ScriptTaskResponse;
import com.example.tool.scripttask.service.component.CryptoService;
import com.example.tool.scripttask.service.ssh.SshConfig;
import com.example.tool.scripttask.service.ssh.SshExecutor;
import com.example.tool.scripttask.service.ssh.SshResult;
import com.example.tool.scripttask.validator.ScriptTaskValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScriptTaskService {

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskLogRepository logRepository;
    private final DeviceRepository deviceRepository;
    private final CryptoService cryptoService;
    private final ScriptTaskValidator validator;
    private final ScriptTaskExecutor scriptTaskExecutor;
    private final SshExecutor sshExecutor;

    @Value("${app.script.ssh.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${app.script.ssh.command-timeout-ms:120000}")
    private long commandTimeoutMs;

    public ScriptTaskService(ScriptTaskRepository taskRepository,
                             ScriptTaskLogRepository logRepository,
                             DeviceRepository deviceRepository,
                             CryptoService cryptoService,
                             ScriptTaskValidator validator,
                             ScriptTaskExecutor scriptTaskExecutor,
                             SshExecutor sshExecutor) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.deviceRepository = deviceRepository;
        this.cryptoService = cryptoService;
        this.validator = validator;
        this.scriptTaskExecutor = scriptTaskExecutor;
        this.sshExecutor = sshExecutor;
    }

    /* ---------------- 查询 ---------------- */

    @Transactional(readOnly = true)
    public List<ScriptTaskResponse> listByDevice(Integer deviceId, Integer userId) {
        requireOwnedDevice(deviceId, userId);
        return taskRepository.findByDevice_DeviceIdAndDevice_UserId(deviceId, userId)
                .stream().map(ScriptTaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ScriptTaskResponse get(Long taskId, Integer userId) {
        return ScriptTaskResponse.from(requireOwnedTask(taskId, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ScriptTaskLogResponse> logs(Long taskId, Integer userId, int page, int size) {
        requireOwnedTask(taskId, userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<ScriptTaskLog> result = logRepository.findByTaskIdOrderByStartedAtDesc(taskId, pageable);
        return PageResponse.of(result, ScriptTaskLogResponse::from);
    }

    @Transactional(readOnly = true)
    public ScriptTaskLogDetailResponse logDetail(Long logId, Integer userId) {
        ScriptTaskLog log = logRepository.findById(logId)
                .orElseThrow(() -> new AccessDeniedException("Log not found or access denied"));
        requireOwnedTask(log.getTaskId(), userId);
        return ScriptTaskLogDetailResponse.from(log);
    }

    /* ---------------- 变更 ---------------- */

    @Transactional
    public ScriptTaskResponse create(Integer deviceId, Integer userId, ScriptTaskRequest request) {
        Device device = requireOwnedDevice(deviceId, userId);
        validator.validate(request, true);

        ScriptTask task = ScriptTask.builder()
                .device(device)
                .name(request.getName())
                .description(request.getDescription())
                .scriptContent(request.getScriptContent())
                .scriptType(request.getScriptType())
                .triggerType(request.getTriggerType())
                .executeAt(request.getTriggerType() == TriggerType.ONCE ? request.getExecuteAt() : null)
                .cronExpression(request.getTriggerType() == TriggerType.CRON ? request.getCronExpression() : null)
                .sshHost(request.getSshHost())
                .sshPort(request.getSshPort())
                .sshUser(request.getSshUser())
                .sshPrivateKeyEncrypted(cryptoService.encrypt(request.getSshPrivateKey()))
                .sshKeyPassphraseEncrypted(cryptoService.encrypt(blankToNull(request.getSshKeyPassphrase())))
                .sudoPasswordEncrypted(cryptoService.encrypt(blankToNull(request.getSudoPassword())))
                .shutdownMode(request.getShutdownMode())
                .shutdownDelaySeconds(request.getShutdownDelaySeconds())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public ScriptTaskResponse update(Long taskId, Integer userId, ScriptTaskRequest request) {
        ScriptTask task = requireOwnedTask(taskId, userId);
        validator.validate(request, false);

        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setScriptContent(request.getScriptContent());
        task.setScriptType(request.getScriptType());
        task.setTriggerType(request.getTriggerType());
        task.setExecuteAt(request.getTriggerType() == TriggerType.ONCE ? request.getExecuteAt() : null);
        task.setCronExpression(request.getTriggerType() == TriggerType.CRON ? request.getCronExpression() : null);
        task.setSshHost(request.getSshHost());
        task.setSshPort(request.getSshPort());
        task.setSshUser(request.getSshUser());
        task.setShutdownMode(request.getShutdownMode());
        task.setShutdownDelaySeconds(request.getShutdownDelaySeconds());
        if (request.getEnabled() != null) {
            task.setEnabled(request.getEnabled());
        }
        if (!isBlank(request.getSshPrivateKey())) {
            task.setSshPrivateKeyEncrypted(cryptoService.encrypt(request.getSshPrivateKey()));
        }
        if (request.getSshKeyPassphrase() != null) {
            task.setSshKeyPassphraseEncrypted(cryptoService.encrypt(blankToNull(request.getSshKeyPassphrase())));
        }
        if (request.getSudoPassword() != null) {
            task.setSudoPasswordEncrypted(cryptoService.encrypt(blankToNull(request.getSudoPassword())));
        }

        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public ScriptTaskResponse toggle(Long taskId, Integer userId) {
        ScriptTask task = requireOwnedTask(taskId, userId);
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long taskId, Integer userId) {
        ScriptTask task = requireOwnedTask(taskId, userId);
        logRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
    }

    @Transactional
    public Map<String, Object> testSsh(TestSshRequest request) {
        SshConfig config = new SshConfig(
                request.getSshHost(), request.getSshPort(), request.getSshUser(),
                request.getSshPrivateKey(), blankToNull(request.getSshKeyPassphrase()));
        String fingerprint = sshExecutor.testConnection(config, connectTimeoutMs);
        return Map.of("ok", true, "fingerprint", fingerprint);
    }

    /* ---------------- 执行 ---------------- */

    /**
     * 同步创建日志并返回 logId，异步执行；事务提交后再触发，避免异步任务读不到日志。
     */
    @Transactional
    public Long submit(Long taskId, TriggeredBy triggeredBy) {
        ScriptTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Task not found, id: " + taskId));

        ScriptTaskLog log = ScriptTaskLog.builder()
                .taskId(task.getId())
                .deviceId(task.getDevice().getDeviceId())
                .triggeredBy(triggeredBy)
                .startedAt(LocalDateTime.now())
                .shutdownTriggered(false)
                .build();
        Long logId = logRepository.save(log).getId();
        Long finalTaskId = task.getId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scriptTaskExecutor.run(finalTaskId, logId, triggeredBy);
                }
            });
        } else {
            scriptTaskExecutor.run(finalTaskId, logId, triggeredBy);
        }
        return logId;
    }

    /**
     * 手动关机：忽略任务配置，立即关机。同步执行并记录一条日志。
     */
    @Transactional
    public Map<String, Object> shutdownNow(Long taskId, Integer userId) {
        ScriptTask task = requireOwnedTask(taskId, userId);
        LocalDateTime startedAt = LocalDateTime.now();

        boolean withPassword = task.getSudoPasswordEncrypted() != null;
        SshResult result;
        try {
            SshConfig config = new SshConfig(
                    task.getSshHost(), task.getSshPort(), task.getSshUser(),
                    cryptoService.decrypt(task.getSshPrivateKeyEncrypted()),
                    cryptoService.decrypt(task.getSshKeyPassphraseEncrypted()));
            String command = ShutdownCommands.immediate(task.getScriptType(), withPassword);
            String stdin = withPassword ? cryptoService.decrypt(task.getSudoPasswordEncrypted()) + "\n" : null;
            result = sshExecutor.executeCommand(config, command, stdin, commandTimeoutMs);
        } catch (Exception e) {
            result = SshResult.error(e.getMessage());
        }

        String error = result.success() ? null : ShutdownCommands.describeFailure(result);
        logRepository.save(ScriptTaskLog.builder()
                .taskId(task.getId())
                .deviceId(task.getDevice().getDeviceId())
                .triggeredBy(TriggeredBy.MANUAL)
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .exitCode(result.exitCode())
                .stdout(result.stdout())
                .stderr(result.stderr())
                .shutdownTriggered(result.success())
                .errorMessage(error)
                .build());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shutdownTriggered", result.success());
        response.put("exitCode", result.exitCode());
        response.put("errorMessage", error);
        return response;
    }

    /** 设备删除时清理其任务与日志。 */
    @Transactional
    public void deleteAllByDevice(Integer deviceId) {
        logRepository.deleteByDeviceId(deviceId);
        taskRepository.deleteAll(taskRepository.findByDevice_DeviceId(deviceId));
    }

    /* ---------------- 权限 ---------------- */

    private Device requireOwnedDevice(Integer deviceId, Integer userId) {
        return deviceRepository.findByDeviceIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new AccessDeniedException("Device not found or access denied"));
    }

    private ScriptTask requireOwnedTask(Long taskId, Integer userId) {
        return taskRepository.findByIdAndDevice_UserId(taskId, userId)
                .orElseThrow(() -> new AccessDeniedException("Task not found or access denied"));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
