package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.repository.ScriptTaskLogRepository;
import com.example.tool.scripttask.repository.ScriptTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监听设备上线事件，触发目标包含该设备的 ON_BOOT 任务（仅在该设备上执行）。
 * 冷却按「任务 + 设备」维度，避免网络抖动引发风暴。
 */
@Slf4j
@Component
public class DeviceOnlineListener {

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskLogRepository logRepository;
    private final ScriptTaskService scriptTaskService;

    @Value("${app.script.on-boot-cooldown-seconds:300}")
    private long cooldownSeconds;

    public DeviceOnlineListener(ScriptTaskRepository taskRepository,
                                ScriptTaskLogRepository logRepository,
                                ScriptTaskService scriptTaskService) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.scriptTaskService = scriptTaskService;
    }

    @Async("scriptTaskThreadPool")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        try {
            List<ScriptTask> tasks = taskRepository.findByTriggerTypeAndEnabledTrueAndTargets_DeviceId(
                    TriggerType.ON_BOOT, event.deviceId());
            if (tasks.isEmpty()) {
                return;
            }
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(cooldownSeconds);
            for (ScriptTask task : tasks) {
                ScriptTaskLog last = logRepository
                        .findFirstByTaskIdAndDeviceIdOrderByStartedAtDesc(task.getId(), event.deviceId())
                        .orElse(null);
                if (last != null && last.getStartedAt() != null && last.getStartedAt().isAfter(threshold)) {
                    log.debug("Skip ON_BOOT task {} on device {} (within {}s cooldown)",
                            task.getId(), event.deviceId(), cooldownSeconds);
                    continue;
                }
                try {
                    scriptTaskService.submitForDevice(task.getId(), event.deviceId(), TriggeredBy.HEARTBEAT);
                } catch (Exception e) {
                    log.error("Failed to submit ON_BOOT task {} on device {}", task.getId(), event.deviceId(), e);
                }
            }
        } catch (Exception e) {
            log.error("DeviceOnlineListener failed for device {}", event.deviceId(), e);
        }
    }
}
