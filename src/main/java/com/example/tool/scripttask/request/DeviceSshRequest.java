package com.example.tool.scripttask.request;

import com.example.tool.scripttask.entity.ScriptType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceSshRequest {

    @NotBlank(message = "SSH 主机不能为空")
    private String sshHost;

    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    @NotBlank(message = "SSH 用户名不能为空")
    private String sshUser;

    /** 目标机 shell 类型，决定关机命令；默认 BASH */
    private ScriptType sshType;

    /** 首次配置必填；更新时留空表示保持原私钥不变 */
    private String sshPrivateKey;

    /** 可选；留空表示保持原口令不变 */
    private String sshKeyPassphrase;

    /** 可选；sudo 密码。留空表示不用密码（走 sudo -n） */
    private String sudoPassword;
}
