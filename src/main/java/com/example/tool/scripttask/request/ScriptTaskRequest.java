package com.example.tool.scripttask.request;

import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ScriptTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称过长")
    private String name;

    @Size(max = 1000, message = "描述过长")
    private String description;

    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;

    @NotNull(message = "脚本类型不能为空")
    private ScriptType scriptType;

    @NotNull(message = "触发类型不能为空")
    private TriggerType triggerType;

    /** ONCE 模式必填 */
    private LocalDateTime executeAt;

    /** CRON 模式必填 */
    private String cronExpression;

    @NotBlank(message = "SSH 主机不能为空")
    private String sshHost;

    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    @NotBlank(message = "SSH 用户名不能为空")
    private String sshUser;

    /** 创建时必填；更新时留空表示保持原私钥不变 */
    private String sshPrivateKey;

    /** 可选；更新时留空表示保持原口令不变 */
    private String sshKeyPassphrase;

    /** 可选；目标机 sudo 密码。留空/不传表示不用密码（走 sudo -n） */
    private String sudoPassword;

    @NotNull(message = "关机模式不能为空")
    private ShutdownMode shutdownMode;

    private Integer shutdownDelaySeconds;

    private Boolean enabled;
}
