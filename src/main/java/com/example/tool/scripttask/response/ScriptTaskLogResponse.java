package com.example.tool.scripttask.response;

import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.TriggeredBy;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 日志摘要（不含 stdout/stderr）。
 */
@Getter
@Setter
public class ScriptTaskLogResponse {

    private Long id;
    private Long taskId;
    private Integer deviceId;
    private TriggeredBy triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer exitCode;
    private Boolean shutdownTriggered;
    private String errorMessage;
    private boolean running;

    public static ScriptTaskLogResponse from(ScriptTaskLog log) {
        ScriptTaskLogResponse r = new ScriptTaskLogResponse();
        r.id = log.getId();
        r.taskId = log.getTaskId();
        r.deviceId = log.getDeviceId();
        r.triggeredBy = log.getTriggeredBy();
        r.startedAt = log.getStartedAt();
        r.finishedAt = log.getFinishedAt();
        r.exitCode = log.getExitCode();
        r.shutdownTriggered = log.getShutdownTriggered();
        r.errorMessage = log.getErrorMessage();
        r.running = log.getFinishedAt() == null;
        return r;
    }
}
