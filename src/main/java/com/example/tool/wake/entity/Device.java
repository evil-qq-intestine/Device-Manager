package com.example.tool.wake.entity;

import jakarta.persistence.*;
import jakarta.persistence.Entity;

@Entity
@Table(name = "device")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增ID
    private int id;

    @Column(name = "user_id")
    private int userId;          // 先存用户ID，等User类建好后再改成对象关联

    @Column(nullable = false, unique = true)
    private String mac;

    private String ip;
    private String name;

    // JPA 要求的无参构造器
    public Device() {
    }

    // 业务用的全参构造器（不含id，id由数据库生成）
    public Device(int userId, String mac, String ip, String name) {
        this.userId = userId;
        this.mac = mac;
        this.ip = ip;
        this.name = name;
    }

    // 所有 getter/setter
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }
    public void setUserId(int userId) {
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
}