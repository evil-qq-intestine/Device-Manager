package com.example.tool.device.validator;

import com.example.tool.device.entity.DeviceMonitor;
import com.example.tool.device.entity.DeviceMonitorModeEnum;
import org.springframework.stereotype.Component;

@Component
public class DeviceMonitorValidator {
    public void validateBeforeUpdate(DeviceMonitor deviceMonitor) {
        if (deviceMonitor.getPingTimeout() != null && deviceMonitor.getPingInterval() != null) {
            if (deviceMonitor.getPingTimeout() >= deviceMonitor.getPingInterval()) {
                throw new RuntimeException("ping超时时间(" + deviceMonitor.getPingTimeout()
                        + ") 必须小于检测间隔(" + deviceMonitor.getPingInterval() + ")");
            }
        }

        if (deviceMonitor.getMonitorMode() != null) {
            if (DeviceMonitorModeEnum.PING != deviceMonitor.getMonitorMode() && DeviceMonitorModeEnum.HEARTBEAT != deviceMonitor.getMonitorMode()) {
                throw new RuntimeException("监控模式只能为 PING 或 HEARTBEAT");
            }
        }
    }
}
