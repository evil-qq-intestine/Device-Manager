package com.example.tool.device.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "device_monitor")
public class DeviceMonitor {
    @Id
    @Column(columnDefinition = "INT", name = "monitor_id")
    private Integer monitorId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "device_id")
    @JsonIgnore
    private Device device;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ipmode", length = 10)
    private DeviceIpModeEnum ipMode = DeviceIpModeEnum.IPV4;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "monitormode")
    private DeviceMonitorModeEnum monitorMode = DeviceMonitorModeEnum.PING;

    @Builder.Default
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingInterval = 30;//PING时间间隔
    @Builder.Default
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer responseTimeout = 60;//离线最大容忍
    @Builder.Default
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer pingTimeout = 3;//PING命令超时
    @Builder.Default
    @Column(columnDefinition = "INTEGER COMMENT '单位：秒'")
    private Integer wakeTimeout = 120;//开机(唤醒)后等待上线的独立超时
    @JsonIgnore
    private LocalDateTime lastOnlineTime;//上次在线时间

    @JsonIgnore
    private LocalDateTime probeStartTime;//PING开始的时间

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DeviceStatusEnum status;

}
