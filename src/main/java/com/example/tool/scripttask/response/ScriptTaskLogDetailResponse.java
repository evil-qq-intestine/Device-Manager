package com.example.tool.scripttask.response;

import com.example.tool.scripttask.entity.ScriptTaskLog;
import com.example.tool.scripttask.entity.TriggeredBy;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 单次日志详情（含完整输出）。
 */
@Getter
@Setter
public class ScriptTaskLogDetailResponse {

    private Long id;
    private Long taskId;
    private Integer deviceId;
    private TriggeredBy triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private Boolean shutdownTriggered;
    private String errorMessage;
    private boolean running;

    public static ScriptTaskLogDetailResponse from(ScriptTaskLog log) {
        ScriptTaskLogDetailResponse r = new ScriptTaskLogDetailResponse();
        r.id = log.getId();
        r.taskId = log.getTaskId();
        r.deviceId = log.getDeviceId();
        r.triggeredBy = log.getTriggeredBy();
        r.startedAt = log.getStartedAt();
        r.finishedAt = log.getFinishedAt();
        r.exitCode = log.getExitCode();
        r.stdout = log.getStdout();
        r.stderr = log.getStderr();
        r.shutdownTriggered = log.getShutdownTriggered();
        r.errorMessage = log.getErrorMessage();
        r.running = log.getFinishedAt() == null;
        return r;
    }
}
