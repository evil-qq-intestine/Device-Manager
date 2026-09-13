package com.example.tool.scripttask.request;

import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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

    @NotNull(message = "触发类型不能为空")
    private TriggerType triggerType;

    /** ONCE 模式必填 */
    private LocalDateTime executeAt;

    /** CRON 模式必填 */
    private String cronExpression;

    @NotNull(message = "关机模式不能为空")
    private ShutdownMode shutdownMode;

    private Integer shutdownDelaySeconds;

    private Boolean enabled;

    /** 目标设备，至少一台；SSH 连接信息取各设备的设备 SSH 配置 */
    @NotEmpty(message = "至少选择一台目标设备")
    private List<Integer> targetDeviceIds;
}
