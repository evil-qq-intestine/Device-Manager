package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.repository.ScriptTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监听设备上线事件，触发该设备的 ON_BOOT 任务。带冷却，避免网络抖动引发风暴。
 */
@Slf4j
@Component
public class DeviceOnlineListener {

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskService scriptTaskService;

    @Value("${app.script.on-boot-cooldown-seconds:300}")
    private long cooldownSeconds;

    public DeviceOnlineListener(ScriptTaskRepository taskRepository, ScriptTaskService scriptTaskService) {
        this.taskRepository = taskRepository;
        this.scriptTaskService = scriptTaskService;
    }

    @Async("scriptTaskThreadPool")
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        try {
            List<ScriptTask> tasks = taskRepository.findByDevice_DeviceIdAndTriggerTypeAndEnabledTrue(
                    event.deviceId(), TriggerType.ON_BOOT);
            if (tasks.isEmpty()) {
                return;
            }
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(cooldownSeconds);
            for (ScriptTask task : tasks) {
                if (task.getLastExecutedAt() != null && task.getLastExecutedAt().isAfter(threshold)) {
                    log.debug("Skip ON_BOOT task {} (within {}s cooldown)", task.getId(), cooldownSeconds);
                    continue;
                }
                try {
                    scriptTaskService.submit(task.getId(), TriggeredBy.HEARTBEAT);
                } catch (Exception e) {
                    log.error("Failed to submit ON_BOOT task {}", task.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("DeviceOnlineListener failed for device {}", event.deviceId(), e);
        }
    }
}
