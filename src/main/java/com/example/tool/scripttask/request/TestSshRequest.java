package com.example.tool.scripttask.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestSshRequest {

    @NotBlank(message = "SSH 主机不能为空")
    private String sshHost;

    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口范围 1-65535")
    @Max(value = 65535, message = "SSH 端口范围 1-65535")
    private Integer sshPort;

    @NotBlank(message = "SSH 用户名不能为空")
    private String sshUser;

    @NotBlank(message = "私钥不能为空")
    private String sshPrivateKey;

    private String sshKeyPassphrase;
}
