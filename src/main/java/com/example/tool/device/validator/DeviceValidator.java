package com.example.tool.device.validator;

import com.example.tool.device.entity.Device;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class DeviceValidator {
    private final DeviceRepository deviceRepository;
    public DeviceValidator(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void validateBeforeUpdate(Device device, Device existing) {
        if (device.getMac() != null && !device.getMac().equals(existing.getMac())) {
            if (deviceRepository.existsByMac(device.getMac())) {
                throw new BusinessException("MAC address already used by another device: " + device.getMac());
            }
        }
    }

    public void validateBeforeSave(Device device) {
        if(deviceRepository.existsByMac(device.getMac())) {
            throw new BusinessException("Device MAC address already exists: " + device.getMac());
        }
        if(device.getDeviceToken() != null) {
            device.setDeviceToken(generateDeviceToken());
        }
    }

    private String generateDeviceToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
}