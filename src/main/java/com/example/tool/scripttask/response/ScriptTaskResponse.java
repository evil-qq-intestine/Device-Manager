package com.example.tool.scripttask.response;

import com.example.tool.device.entity.Device;
import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务对外视图：一个脚本 + 多个目标设备，不含任何密钥。
 * 每个目标带各自「最近一次执行结果」，便于前端按设备汇总。
 */
@Getter
@Setter
public class ScriptTaskResponse {

    private Long id;
    private String name;
    private String description;
    private String scriptContent;
    private TriggerType triggerType;
    private LocalDateTime executeAt;
    private String cronExpression;
    private ShutdownMode shutdownMode;
    private Integer shutdownDelaySeconds;
    private Boolean enabled;
    private LocalDateTime lastExecutedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TargetInfo> targets;
    private int targetCount;

    @Getter
    @Setter
    public static class TargetInfo {
        private Integer deviceId;
        private String name;
        private String mac;
        private boolean sshConfigured;
        private Integer lastExitCode;
        private LocalDateTime lastFinishedAt;
        private boolean lastRunning;

        public static TargetInfo from(Device device, ScriptTaskLog lastLog) {
            TargetInfo t = new TargetInfo();
            t.deviceId = device.getDeviceId();
            t.name = device.getDeviceName();
            t.mac = device.getMac();
            t.sshConfigured = device.getSshPrivateKeyEncrypted() != null;
            if (lastLog != null) {
                t.lastExitCode = lastLog.getExitCode();
                t.lastFinishedAt = lastLog.getFinishedAt();
                t.lastRunning = lastLog.getFinishedAt() == null;
            }
            return t;
        }
    }

    public static ScriptTaskResponse from(ScriptTask task) {
        return from(task, Map.of());
    }

    public static ScriptTaskResponse from(ScriptTask task, Map<Integer, ScriptTaskLog> latestByDevice) {
        ScriptTaskResponse r = new ScriptTaskResponse();
        r.id = task.getId();
        r.name = task.getName();
        r.description = task.getDescription();
        r.scriptContent = task.getScriptContent();
        r.triggerType = task.getTriggerType();
        r.executeAt = task.getExecuteAt();
        r.cronExpression = task.getCronExpression();
        r.shutdownMode = task.getShutdownMode();
        r.shutdownDelaySeconds = task.getShutdownDelaySeconds();
        r.enabled = task.getEnabled();
        r.lastExecutedAt = task.getLastExecutedAt();
        r.createdAt = task.getCreatedAt();
        r.updatedAt = task.getUpdatedAt();
        r.targets = task.getTargets().stream()
                .map(d -> TargetInfo.from(d, latestByDevice.get(d.getDeviceId())))
                .toList();
        r.targetCount = r.targets.size();
        return r;
    }
}
