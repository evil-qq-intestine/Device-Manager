package com.example.tool.device.validator;

import com.example.tool.device.entity.DeviceMonitor;
import com.example.tool.device.entity.DeviceMonitorModeEnum;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class DeviceMonitorValidator {
    public void validateBeforeUpdate(DeviceMonitor deviceMonitor) {
        if (deviceMonitor.getMonitorMode() != null) {
            if (DeviceMonitorModeEnum.PING != deviceMonitor.getMonitorMode() && DeviceMonitorModeEnum.HEARTBEAT != deviceMonitor.getMonitorMode()) {
                throw new BusinessException("Monitor mode must be PING or HEARTBEAT");
            }
        }

        if (deviceMonitor.getPingTimeout() != null && deviceMonitor.getPingInterval() != null) {
            if (deviceMonitor.getPingTimeout() >= deviceMonitor.getPingInterval()) {
                throw new BusinessException("Ping timeout (" + deviceMonitor.getPingTimeout()
                        + ") must be less than check interval (" + deviceMonitor.getPingInterval() + ")");
            }
        }

        if (deviceMonitor.getWakeTimeout() != null && deviceMonitor.getWakeTimeout() <= 0) {
            throw new BusinessException("Wake timeout must be a positive number of seconds");
        }
    }
}
