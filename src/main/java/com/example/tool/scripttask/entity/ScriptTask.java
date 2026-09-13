package com.example.tool.scripttask.entity;

import com.example.tool.device.entity.Device;
import com.example.tool.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户级脚本任务：一个脚本可挂到多台目标设备，触发方式与关机配置为脚本级共享。
 * 目标设备的 SSH 连接信息复用各自设备的「设备 SSH 配置」，脚本本身不存密钥。
 */
@Getter
@Setter
@Entity
@Table(name = "script_task")
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ScriptTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User owner;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "script_task_target",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "device_id"))
    @JsonIgnore
    @ToString.Exclude
    private Set<Device> targets = new HashSet<>();

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String scriptContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    /** ONCE：精确执行时间 */
    private LocalDateTime executeAt;

    /** CRON：cron 表达式 */
    private String cronExpression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShutdownMode shutdownMode;

    private Integer shutdownDelaySeconds;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    private LocalDateTime lastExecutedAt;
    private Integer lastExitCode;

    @Column(columnDefinition = "TEXT")
    private String lastOutput;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.enabled == null) {
            this.enabled = true;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
