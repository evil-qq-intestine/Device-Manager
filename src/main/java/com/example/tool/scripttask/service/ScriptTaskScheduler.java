package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.entity.TriggeredBy;
import com.example.tool.scripttask.repository.ScriptTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时扫描 ONCE / CRON 任务。
 */
@Slf4j
@Component
public class ScriptTaskScheduler {

    private final ScriptTaskRepository taskRepository;
    private final ScriptTaskService scriptTaskService;

    public ScriptTaskScheduler(ScriptTaskRepository taskRepository, ScriptTaskService scriptTaskService) {
        this.taskRepository = taskRepository;
        this.scriptTaskService = scriptTaskService;
    }

    @Scheduled(fixedDelayString = "${app.script.scan-interval-ms:60000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        try {
            scriptTaskService.preWakeDueTasks(now);
        } catch (Exception e) {
            log.error("Pre-wake scan failed", e);
        }
        scanOnce(now);
        scanCron(now);
    }

    private void scanOnce(LocalDateTime now) {
        for (ScriptTask task : taskRepository.findByTriggerTypeAndEnabledTrue(TriggerType.ONCE)) {
            if (task.getExecuteAt() == null || task.getExecuteAt().isAfter(now)) {
                continue;
            }
            // 先禁用，防止重复触发
            task.setEnabled(false);
            taskRepository.save(task);
            try {
                scriptTaskService.submit(task.getId(), TriggeredBy.SCHEDULE);
            } catch (Exception e) {
                log.error("Failed to submit ONCE task {}", task.getId(), e);
            }
        }
    }

    private void scanCron(LocalDateTime now) {
        for (ScriptTask task : taskRepository.findByTriggerTypeAndEnabledTrue(TriggerType.CRON)) {
            if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
                continue;
            }
            try {
                CronExpression expression = CronExpression.parse(task.getCronExpression());
                LocalDateTime reference = task.getLastExecutedAt() != null
                        ? task.getLastExecutedAt()
                        : (task.getCreatedAt() != null ? task.getCreatedAt() : now.minusMinutes(1));
                LocalDateTime next = expression.next(reference);
                if (next != null && !next.isAfter(now)) {
                    task.setLastExecutedAt(now);
                    taskRepository.save(task);
                    scriptTaskService.submit(task.getId(), TriggeredBy.SCHEDULE);
                }
            } catch (Exception e) {
                log.error("Invalid cron expression for task {}: {}", task.getId(), task.getCronExpression(), e);
            }
        }
    }
}
