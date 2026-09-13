package com.example.tool.scripttask.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 单次脚本执行日志。finished_at 为空表示仍在执行中。
 */
@Getter
@Setter
@Entity
@Table(name = "script_task_log")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Integer deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggeredBy triggeredBy;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    @Builder.Default
    private Boolean shutdownTriggered = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
