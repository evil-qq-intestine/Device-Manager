package com.example.tool.scripttask.response;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 任务对外视图：绝不包含私钥/口令明文或密文。
 */
@Getter
@Setter
public class ScriptTaskResponse {

    private Long id;
    private Integer deviceId;
    private String name;
    private String description;
    private String scriptContent;
    private ScriptType scriptType;
    private TriggerType triggerType;
    private LocalDateTime executeAt;
    private String cronExpression;
    private String sshHost;
    private Integer sshPort;
    private String sshUser;
    private boolean hasPrivateKey;
    private boolean hasPassphrase;
    private boolean hasSudoPassword;
    private ShutdownMode shutdownMode;
    private Integer shutdownDelaySeconds;
    private Boolean enabled;
    private LocalDateTime lastExecutedAt;
    private Integer lastExitCode;
    private String lastOutput;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScriptTaskResponse from(ScriptTask task) {
        ScriptTaskResponse r = new ScriptTaskResponse();
        r.id = task.getId();
        r.deviceId = task.getDevice() != null ? task.getDevice().getDeviceId() : null;
        r.name = task.getName();
        r.description = task.getDescription();
        r.scriptContent = task.getScriptContent();
        r.scriptType = task.getScriptType();
        r.triggerType = task.getTriggerType();
        r.executeAt = task.getExecuteAt();
        r.cronExpression = task.getCronExpression();
        r.sshHost = task.getSshHost();
        r.sshPort = task.getSshPort();
        r.sshUser = task.getSshUser();
        r.hasPrivateKey = task.getSshPrivateKeyEncrypted() != null;
        r.hasPassphrase = task.getSshKeyPassphraseEncrypted() != null;
        r.hasSudoPassword = task.getSudoPasswordEncrypted() != null;
        r.shutdownMode = task.getShutdownMode();
        r.shutdownDelaySeconds = task.getShutdownDelaySeconds();
        r.enabled = task.getEnabled();
        r.lastExecutedAt = task.getLastExecutedAt();
        r.lastExitCode = task.getLastExitCode();
        r.lastOutput = task.getLastOutput();
        r.createdAt = task.getCreatedAt();
        r.updatedAt = task.getUpdatedAt();
        return r;
    }
}
