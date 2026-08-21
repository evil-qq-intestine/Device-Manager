package com.example.tool.wake.validator;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.entity.DeviceMonitorMode;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.util.MacUtils;
import org.springframework.stereotype.Component;

@Component
public class DeviceValidator {

    public void validateBeforeUpdate(Device device, Device existing, DeviceRepository repo) {
        if (device.getMac() != null && !device.getMac().equals(existing.getMac())) {
            MacUtils.checkMac(device.getMac()); // 格式校验
            if (repo.existsByMac(device.getMac())) {
                throw new RuntimeException("MAC地址已被其他设备占用：" + device.getMac());
            }
        }

        if (device.getPingTimeout() != null && device.getPingInterval() != null) {
            if (device.getPingTimeout() >= device.getPingInterval()) {
                throw new RuntimeException("ping超时时间(" + device.getPingTimeout()
                        + ") 必须小于检测间隔(" + device.getPingInterval() + ")");
            }
        }

        if (device.getMonitorMode() != null) {
            if (DeviceMonitorMode.PING != device.getMonitorMode() && DeviceMonitorMode.HEARTBEAT != device.getMonitorMode()) {
                throw new RuntimeException("监控模式只能为 PING 或 HEARTBEAT");
            }
        }
    }
}
