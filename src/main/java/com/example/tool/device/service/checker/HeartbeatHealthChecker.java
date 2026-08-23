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
            log.error("无设备信息，无法计算心跳是否超时");
            return false;
        }
        LocalDateTime lastOnlineTime = device.getLastOnlineTime();
        if (lastOnlineTime == null) {
            log.error("无设备心跳超时设定，请检查是否已设置心跳超时时间");
            return false;
        }
        return device.getResponseTimeout() > Duration.between(lastOnlineTime, LocalDateTime.now()).getSeconds();
    }
}