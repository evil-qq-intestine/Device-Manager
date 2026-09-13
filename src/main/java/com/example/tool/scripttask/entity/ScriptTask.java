package com.example.tool.scripttask.entity;

import com.example.tool.device.entity.Device;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 通过 SSH 在目标设备上远程执行的脚本任务。
 * 私钥与口令以密文存储，永不通过接口返回。
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
    @JoinColumn(name = "device_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Device device;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String scriptContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScriptType scriptType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    /** ONCE：精确执行时间 */
    private LocalDateTime executeAt;

    /** CRON：cron 表达式 */
    private String cronExpression;

    private String sshHost;
    private Integer sshPort;
    private String sshUser;

    @Column(columnDefinition = "TEXT")
    private String sshPrivateKeyEncrypted;

    @Column(columnDefinition = "TEXT")
    private String sshKeyPassphraseEncrypted;

    @Column(columnDefinition = "TEXT")
    private String sudoPasswordEncrypted;

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
