package com.example.tool.device.service.checker;

import com.example.tool.device.entity.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component("heartbeatHealthChecker")
public class HeartbeatHealthChecker implements DeviceHealthChecker {
    @Override
    public boolean isAlive(Device device){
        if (device == null) {
            log.error("No device info, cannot check heartbeat timeout");
            return false;
        }
        LocalDateTime lastOnlineTime = device.getLastOnlineTime();
        if (lastOnlineTime == null) {
            log.error("No heartbeat timeout configured, please check whether the heartbeat timeout is set");
            return false;
        }
        return device.getResponseTimeout() > Duration.between(lastOnlineTime, LocalDateTime.now()).getSeconds();
    }
}