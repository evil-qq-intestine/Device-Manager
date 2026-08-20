package com.example.tool.wake.service;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.util.SystemPingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component("ping")
public class PingHealthChecker implements DeviceHealthChecker {
    @Override
    public boolean isAlive(Device device){
        if (device == null) {
            log.warn("没有设备信息，无法 Ping");
            return false;
        }
        int timeoutSeconds = 3;
        if (device.getPingTimeout() != null){
            timeoutSeconds = device.getPingTimeout();
        }
        return SystemPingUtil.ping(device.getIp(), timeoutSeconds);
    }
}
