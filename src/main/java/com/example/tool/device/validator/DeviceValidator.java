package com.example.tool.device.validator;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceMonitorModeEnum;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.util.MacUtils;
import org.springframework.stereotype.Component;

@Component
public class DeviceValidator {
    // 注入 Repository 用于查重
    private final DeviceRepository deviceRepository;
    public DeviceValidator(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void validateBeforeUpdate(Device device, Device existing) {
        // 1. 格式校验（如果实体类上已有 @Pattern，这行可以省略）
        if (device.getMac() != null) {
            MacUtils.checkMac(device.getMac());
        }

        // 2. 唯一性校验（关键是：传入 existing 用来排除自己）
        if (device.getMac() != null && !device.getMac().equals(existing.getMac())) {
            if (deviceRepository.existsByMac(device.getMac())) {
                throw new RuntimeException("MAC地址已被其他设备占用：" + device.getMac());
            }
        }

        // 3. 可以再加点别的，比如 IP 格式检查
    }
}