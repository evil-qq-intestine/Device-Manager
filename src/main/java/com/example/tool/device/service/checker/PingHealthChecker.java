package com.example.tool.device.service.checker;

import com.example.tool.device.entity.Device;
import com.example.tool.device.util.SystemPingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("pingHealthChecker")
public class PingHealthChecker implements DeviceHealthChecker {
    @Override
    public boolean isAlive(Device device){
        if (device == null) {
            log.warn("No device info, cannot ping");
            return false;
        }
        int timeoutSeconds = 3;
        if (device.getPingTimeout() != null){
            timeoutSeconds = device.getPingTimeout();
        }
        return SystemPingUtil.ping(device.getIp(), timeoutSeconds);
    }
}
