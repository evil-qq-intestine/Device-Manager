package com.example.tool.device.entity;

import com.example.tool.user.entity.User;
import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "device")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true) // toBuilder = true 允许基于现有对象修改，更新好用
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增ID
    @JsonIgnore
    private Integer deviceId;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "MAC地址不能为空")
    @Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$", message = "MAC地址格式无效")
    private String mac;

    public static final String IP_REGEX =
                    "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|" +
                    "(([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:)|([0-9a-fA-F]{1,4}:){1,6}:([0-9a-fA-F]{1,4})?|" +
                    "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                    "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                    "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
                    "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|" +
                    "::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|" +
                    "([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$";

    @Pattern(regexp = IP_REGEX, message = "非法的 IPv4 或 IPv6 地址")
    private String ip;

    private String deviceName;

    @Column(unique = true, nullable = false)
    @JsonIgnore
    private String deviceToken;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private DeviceMonitor monitor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private List<ScriptTask> scriptTasks = new ArrayList<>();

    // ---- 设备级 SSH 配置（用于独立关机等，不依赖脚本任务）----

    private String sshHost;
    private Integer sshPort;
    private String sshUser;

    @Enumerated(EnumType.STRING)
    private ScriptType sshType = ScriptType.BASH;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String sshPrivateKeyEncrypted;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String sshKeyPassphraseEncrypted;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String sudoPasswordEncrypted;

    public boolean getSshConfigured() {
        return sshPrivateKeyEncrypted != null;
    }

    public DeviceMonitorModeEnum getMonitorMode() {
        return monitor != null ? monitor.getMonitorMode() : DeviceMonitorModeEnum.PING;
    }
    public void setMonitorMode(DeviceMonitorModeEnum monitorMode) {
        ensureMonitor();
        this.monitor.setMonitorMode(monitorMode);
    }

    public Integer getPingInterval() {
        return monitor != null ? monitor.getPingInterval() : 30;
    }
    public void setPingInterval(Integer pingInterval) {
        ensureMonitor();
        this.monitor.setPingInterval(pingInterval);
    }

    public Integer getPingTimeout() {
        return monitor != null ? monitor.getPingTimeout() : 3;
    }
    public void setPingTimeout(Integer pingTimeout) {
        ensureMonitor();
        this.monitor.setPingTimeout(pingTimeout);
    }

    public LocalDateTime getLastOnlineTime() {
        return monitor != null ? monitor.getLastOnlineTime() : null;
    }
    public void setLastOnlineTime(LocalDateTime lastOnlineTime) {
        ensureMonitor();
        this.monitor.setLastOnlineTime(lastOnlineTime);
    }

    public Integer getResponseTimeout() {
        return (monitor != null && monitor.getResponseTimeout() != null) ? monitor.getResponseTimeout() : 60;
    }
    public void setResponseTimeout(Integer responseTimeout) {
        ensureMonitor();
        this.monitor.setResponseTimeout(responseTimeout);
    }

    public Integer getWakeTimeout() {
        return (monitor != null && monitor.getWakeTimeout() != null) ? monitor.getWakeTimeout() : 120;
    }
    public void setWakeTimeout(Integer wakeTimeout) {
        ensureMonitor();
        this.monitor.setWakeTimeout(wakeTimeout);
    }

    public DeviceIpModeEnum getIpMode() {
        return monitor != null ? monitor.getIpMode() : DeviceIpModeEnum.IPV4;
    }
    public void setIpMode(DeviceIpModeEnum ipMode) {
        ensureMonitor();
        this.monitor.setIpMode(ipMode);
    }

    public LocalDateTime getProbeStartTime(){
        return monitor != null ? monitor.getProbeStartTime() : null;
    }
    public void setProbeStartTime(LocalDateTime probeStartTime) {
        ensureMonitor();
        this.monitor.setProbeStartTime(probeStartTime);
    }

    public DeviceStatusEnum getStatus() {
        return monitor != null ? monitor.getStatus() : DeviceStatusEnum.UNKNOWN;
    }
    public void setStatus(DeviceStatusEnum deviceStatus) {
        ensureMonitor();
        this.monitor.setStatus(deviceStatus);
    }

    public void ensureMonitor() {
        if (this.monitor == null) {
            this.monitor = DeviceMonitor.builder()
                    .status(DeviceStatusEnum.UNKNOWN)
                    .monitorMode(DeviceMonitorModeEnum.PING)
                    .ipMode(DeviceIpModeEnum.getIpMode(ip))
                    .pingInterval(120)
                    .pingTimeout(3)
                    .responseTimeout(60)
                    .wakeTimeout(120)
                    .build();
            monitor.setDevice(this);
        }
    }
}