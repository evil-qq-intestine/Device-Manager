package com.example.tool.device.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Entity;

import java.time.LocalDateTime;

@Entity
@Table(name = "device")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增ID
    @JsonIgnore
    private Integer deviceId;

    @Column(name = "user_id")
    private Integer userId;          // 先存用户ID，等User类建好后再改成对象关联

    @Column(nullable = false, unique = true)
    private String mac;

    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(name = "ipmode", length = 10)
    private DeviceIpMode ipMode;

    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitormode")
    private DeviceMonitorMode monitorMode;

    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingInterval;
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer heartbeatTimeout;
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingTimeout;
    @JsonIgnore
    private LocalDateTime lastOnlineTime;

    @JsonIgnore
    private LocalDateTime probeStartTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DeviceStatus status;

    private Integer responseTimeout;

    public Device() {}

    public Device(Integer deviceId,
                  Integer userId,
                  String mac,
                  String ip,
                  DeviceIpMode ipMode,
                  String deviceName,
                  DeviceMonitorMode monitorMode,
                  Integer pingInterval,
                  Integer heartbeatTimeout,
                  Integer pingTimeout,
                  LocalDateTime lastOnlineTime,
                  LocalDateTime probeStartTime,
                  DeviceStatus deviceStatus,
                  Integer responseTimeout)
                  {
        this.deviceId = deviceId;
        this.userId = userId;
        this.mac = mac;
        this.ip = ip;
        this.ipMode = ipMode;
        this.deviceName = deviceName;
        this.monitorMode = monitorMode;
        this.pingInterval = pingInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        this.pingTimeout = pingTimeout;
        this.lastOnlineTime = lastOnlineTime;
        this.probeStartTime = probeStartTime;
        this.status = deviceStatus;
        this.responseTimeout = responseTimeout;
    }

    // 所有 getter/setter
    public Integer getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getMac() {
        return mac;
    }
    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getIp() {
        return ip;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }

    public DeviceIpMode getIpMode() {
        return ipMode;
    }
    public void setIpMode(DeviceIpMode ipMode) {
        this.ipMode = ipMode;
    }

    public String getDeviceName() {
        return deviceName;
    }
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public DeviceMonitorMode getMonitorMode() {
        return monitorMode;
    }
    public void setMonitorMode(DeviceMonitorMode monitorMode) {
        this.monitorMode = monitorMode;
    }

    public Integer getPingInterval() {
        return pingInterval;
    }
    public void setPingInterval(Integer pingInterval) {
        this.pingInterval = pingInterval;
    }

    public Integer getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(Integer onlineTimeout) {
        this.heartbeatTimeout = onlineTimeout;
    }

    public Integer getPingTimeout() {
        return pingTimeout;
    }
    public void setPingTimeout(Integer pingTimeout) {
        this.pingTimeout = pingTimeout;
    }

    public LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }
    public void setLastOnlineTime(LocalDateTime lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public DeviceStatus getStatus() {
        return status;
    }
    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public LocalDateTime getProbeStartTime() {
        return probeStartTime;
    }
    public void setProbeStartTime(LocalDateTime probeStartTime) {
        this.probeStartTime = probeStartTime;
    }

    public Integer getResponseTimeout() {
        return responseTimeout;
    }
    public void setResponseTimeout(Integer responseTimeout) {
        this.responseTimeout = responseTimeout;
    }
}