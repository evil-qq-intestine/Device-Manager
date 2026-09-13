package com.example.tool.scripttask.response;

import com.example.tool.device.entity.Device;
import com.example.tool.scripttask.entity.ScriptType;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备级 SSH 配置视图，不含任何密钥/口令。
 */
@Getter
@Setter
public class DeviceSshResponse {

    private String sshHost;
    private Integer sshPort;
    private String sshUser;
    private ScriptType sshType;
    private boolean configured;
    private boolean hasPassphrase;
    private boolean hasSudoPassword;

    public static DeviceSshResponse from(Device device) {
        DeviceSshResponse r = new DeviceSshResponse();
        r.sshHost = device.getSshHost();
        r.sshPort = device.getSshPort();
        r.sshUser = device.getSshUser();
        r.sshType = device.getSshType();
        r.configured = device.getSshPrivateKeyEncrypted() != null;
        r.hasPassphrase = device.getSshKeyPassphraseEncrypted() != null;
        r.hasSudoPassword = device.getSudoPasswordEncrypted() != null;
        return r;
    }
}
