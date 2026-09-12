package com.example.tool.device.validator;

import com.example.tool.device.entity.Device;
import com.example.tool.device.repository.DeviceRepository;
import org.springframework.stereotype.Component;

@Component
public class DeviceValidator {
    // 注入 Repository 用于查重
    private final DeviceRepository deviceRepository;
    public DeviceValidator(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void validateBeforeSave(Device device, Device existing) {
        if (device.getMac() != null && !device.getMac().equals(existing.getMac())) {
            if (deviceRepository.existsByMac(device.getMac())) {
                throw new RuntimeException("MAC地址已被其他设备占用：" + device.getMac());
            }
        }
    }

    public void validateBeforeSave(Device device) {
        if(deviceRepository.existsByMac(device.getMac())) {
            throw new RuntimeException("设备MAC地址已存在：" + device.getMac());
        }
    }
}