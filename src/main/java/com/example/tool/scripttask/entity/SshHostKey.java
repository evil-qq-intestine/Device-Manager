package com.example.tool.scripttask.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 目标主机公钥指纹（首次连接记录，后续连接校验，防中间人）。
 */
@Getter
@Setter
@Entity
@Table(name = "ssh_host_key",
        uniqueConstraints = @UniqueConstraint(name = "uk_ssh_host_key_host_port", columnNames = {"host", "port"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SshHostKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    private String keyType;

    @Column(nullable = false)
    private String fingerprintSha256;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
