package com.example.tool.scripttask.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.ScriptType;
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
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
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
import java.util.*;

@Slf4j
@Service
public class ScriptTaskService {

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskLogRepository logRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
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
                             UserRepository userRepository,
                             CryptoService cryptoService,
                             ScriptTaskValidator validator,
                             ScriptTaskExecutor scriptTaskExecutor,
                             SshExecutor sshExecutor) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
        this.validator = validator;
        this.scriptTaskExecutor = scriptTaskExecutor;
        this.sshExecutor = sshExecutor;
    }

    /* ---------------- 查询 ---------------- */

    @Transactional(readOnly = true)
    public List<ScriptTaskResponse> list(Integer userId) {
        List<ScriptTask> tasks = taskRepository.findByOwner_Id(userId);
        Map<Long, Map<Integer, ScriptTaskLog>> latest = loadLatestByDevice(tasks);
        return tasks.stream()
                .map(t -> ScriptTaskResponse.from(t, latest.getOrDefault(t.getId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScriptTaskResponse get(Long taskId, Integer userId) {
        ScriptTask task = requireOwned(taskId, userId);
        Map<Integer, ScriptTaskLog> latest = loadLatestByDevice(List.of(task)).getOrDefault(taskId, Map.of());
        return ScriptTaskResponse.from(task, latest);
    }

    /** 每个 (任务, 设备) 的最近一次执行结果。 */
    private Map<Long, Map<Integer, ScriptTaskLog>> loadLatestByDevice(List<ScriptTask> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = tasks.stream().map(ScriptTask::getId).toList();
        Map<Long, Map<Integer, ScriptTaskLog>> result = new HashMap<>();
        for (ScriptTaskLog logEntry : logRepository.findLatestPerDeviceByTaskIds(ids)) {
            result.computeIfAbsent(logEntry.getTaskId(), k -> new HashMap<>())
                    .put(logEntry.getDeviceId(), logEntry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<ScriptTaskLogResponse> logs(Long taskId, Integer userId, int page, int size) {
        requireOwned(taskId, userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<ScriptTaskLog> result = logRepository.findByTaskIdOrderByStartedAtDesc(taskId, pageable);
        return PageResponse.of(result, ScriptTaskLogResponse::from);
    }

    @Transactional(readOnly = true)
    public ScriptTaskLogDetailResponse logDetail(Long logId, Integer userId) {
        ScriptTaskLog logEntry = logRepository.findById(logId)
                .orElseThrow(() -> new AccessDeniedException("Log not found or access denied"));
        requireOwned(logEntry.getTaskId(), userId);
        return ScriptTaskLogDetailResponse.from(logEntry);
    }

    /* ---------------- 变更 ---------------- */

    @Transactional
    public ScriptTaskResponse create(Integer userId, ScriptTaskRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found, id: " + userId));
        validator.validate(request, true);

        ScriptTask task = ScriptTask.builder()
                .owner(owner)
                .targets(resolveTargets(userId, request.getTargetDeviceIds()))
                .name(request.getName())
                .description(request.getDescription())
                .scriptContent(request.getScriptContent())
                .triggerType(request.getTriggerType())
                .executeAt(request.getTriggerType() == TriggerType.ONCE ? request.getExecuteAt() : null)
                .cronExpression(request.getTriggerType() == TriggerType.CRON ? request.getCronExpression() : null)
                .shutdownMode(request.getShutdownMode())
                .shutdownDelaySeconds(request.getShutdownDelaySeconds())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public ScriptTaskResponse update(Long taskId, Integer userId, ScriptTaskRequest request) {
        ScriptTask task = requireOwned(taskId, userId);
        validator.validate(request, false);

        task.setTargets(resolveTargets(userId, request.getTargetDeviceIds()));
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setScriptContent(request.getScriptContent());
        task.setTriggerType(request.getTriggerType());
        task.setExecuteAt(request.getTriggerType() == TriggerType.ONCE ? request.getExecuteAt() : null);
        task.setCronExpression(request.getTriggerType() == TriggerType.CRON ? request.getCronExpression() : null);
        task.setShutdownMode(request.getShutdownMode());
        task.setShutdownDelaySeconds(request.getShutdownDelaySeconds());
        if (request.getEnabled() != null) {
            task.setEnabled(request.getEnabled());
        }
        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public ScriptTaskResponse toggle(Long taskId, Integer userId) {
        ScriptTask task = requireOwned(taskId, userId);
        task.setEnabled(!Boolean.TRUE.equals(task.getEnabled()));
        return ScriptTaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long taskId, Integer userId) {
        ScriptTask task = requireOwned(taskId, userId);
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
     * 在所有目标设备上执行：每个目标同步创建日志并返回 logId，事务提交后异步执行。
     */
    @Transactional
    public List<Long> submit(Long taskId, TriggeredBy triggeredBy) {
        ScriptTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Task not found, id: " + taskId));
        List<Long> logIds = new ArrayList<>();
        for (Device device : task.getTargets()) {
            logIds.add(schedule(task, device, triggeredBy));
        }
        return logIds;
    }

    /**
     * 仅在指定设备上执行（用于 ON_BOOT）。
     */
    @Transactional
    public Long submitForDevice(Long taskId, Integer deviceId, TriggeredBy triggeredBy) {
        ScriptTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Task not found, id: " + taskId));
        Device device = task.getTargets().stream()
                .filter(d -> d.getDeviceId().equals(deviceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Device " + deviceId + " is not a target of task " + taskId));
        return schedule(task, device, triggeredBy);
    }

    private Long schedule(ScriptTask task, Device device, TriggeredBy triggeredBy) {
        ScriptTaskLog logEntry = ScriptTaskLog.builder()
                .taskId(task.getId())
                .deviceId(device.getDeviceId())
                .triggeredBy(triggeredBy)
                .startedAt(LocalDateTime.now())
                .shutdownTriggered(false)
                .build();
        Long logId = logRepository.save(logEntry).getId();
        Long taskId = task.getId();
        Integer deviceId = device.getDeviceId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scriptTaskExecutor.run(taskId, logId, deviceId, triggeredBy);
                }
            });
        } else {
            scriptTaskExecutor.run(taskId, logId, deviceId, triggeredBy);
        }
        return logId;
    }

    /**
     * 手动关机：忽略任务配置，立即关闭所有目标设备。
     */
    @Transactional
    public List<Map<String, Object>> shutdownNow(Long taskId, Integer userId) {
        ScriptTask task = requireOwned(taskId, userId);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Device device : task.getTargets()) {
            results.add(shutdownDevice(task, device));
        }
        return results;
    }

    private Map<String, Object> shutdownDevice(ScriptTask task, Device device) {
        LocalDateTime startedAt = LocalDateTime.now();
        SshResult result;
        if (device.getSshPrivateKeyEncrypted() == null) {
            result = SshResult.error("目标设备未配置 SSH");
        } else {
            try {
                boolean withPassword = device.getSudoPasswordEncrypted() != null;
                ScriptType type = device.getSshType() != null ? device.getSshType() : ScriptType.BASH;
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
        }

        String error = result.success() ? null : ShutdownCommands.describeFailure(result);
        logRepository.save(ScriptTaskLog.builder()
                .taskId(task.getId())
                .deviceId(device.getDeviceId())
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
        response.put("deviceId", device.getDeviceId());
        response.put("name", device.getDeviceName());
        response.put("shutdownTriggered", result.success());
        response.put("exitCode", result.exitCode());
        response.put("errorMessage", error);
        return response;
    }

    /** 设备删除时：从所有任务的 targets 中移除，并清理该设备的日志。 */
    @Transactional
    public void detachDevice(Integer deviceId) {
        for (ScriptTask task : taskRepository.findByTargets_DeviceId(deviceId)) {
            task.getTargets().removeIf(d -> d.getDeviceId().equals(deviceId));
            taskRepository.save(task);
        }
        logRepository.deleteByDeviceId(deviceId);
    }

    /* ---------------- 权限 ---------------- */

    private ScriptTask requireOwned(Long taskId, Integer userId) {
        return taskRepository.findByIdAndOwner_Id(taskId, userId)
                .orElseThrow(() -> new AccessDeniedException("Task not found or access denied"));
    }

    private Set<Device> resolveTargets(Integer userId, List<Integer> deviceIds) {
        Set<Integer> requested = new HashSet<>(deviceIds);
        Set<Integer> owned = deviceRepository.findByUserId(userId).stream()
                .map(Device::getDeviceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!owned.containsAll(requested)) {
            throw new AccessDeniedException("Contains devices you do not own");
        }
        return new HashSet<>(deviceRepository.findAllById(requested));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
