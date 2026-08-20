package com.example.tool.wake.entity;

import jakarta.persistence.*;
import jakarta.persistence.Entity;

import java.time.LocalDateTime;

@Entity
@Table(name = "device")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增ID
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;          // 先存用户ID，等User类建好后再改成对象关联

    @Column(nullable = false, unique = true)
    private String mac;

    private String ip;
    private String name;
    private String monitorMode;

    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingInterval;
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer heartbeatTimeout;
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingTimeout;
    private LocalDateTime lastOnlineTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DeviceStatus status;
    //private Integer heartbeatThreshold;

    // JPA 要求的无参构造器
    public Device() {
    }

    // 业务用的全参构造器（不含id，id由数据库生成）
    public Device(Integer id,
                  Integer userId,
                  String mac,
                  String ip,
                  String name,
                  String monitorMode,
                  Integer pingInterval,
                  Integer heartbeatTimeout,
                  Integer pingTimeout,
                  LocalDateTime lastOnlineTime,
                  DeviceStatus deviceStatus)
                  //Integer heartbeatThreshold)
                  {
        this.id = id;
        this.userId = userId;
        this.mac = mac;
        this.ip = ip;
        this.name = name;
        this.monitorMode = monitorMode;
        this.pingInterval = pingInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        this.pingTimeout = pingTimeout;
        this.lastOnlineTime = lastOnlineTime;
        this.status = deviceStatus;
        //this.heartbeatThreshold = heartbeatThreshold;
    }

    // 所有 getter/setter
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
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

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getMonitorMode() {
        return monitorMode;
    }
    public void setMonitorMode(String monitorMode) {
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

//    public Integer getHeartbeatThreshold() {
//        return heartbeatThreshold;
//    }
//    public void setHeartbeatThreshold(Integer heartbeatThreshold) {
//        this.heartbeatThreshold = heartbeatThreshold;
//    }
}