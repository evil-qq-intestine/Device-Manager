package com.example.tool.wake.service;

import com.example.tool.wake.entity.Device;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.Map;

public class DeviceHealthCheckManager {
    @Autowired
    private Map<String, DeviceHealthChecker> checkerMap;

    public boolean checkDevice(Device device) {
        String mode = device.getMonitorMode().toLowerCase();
        String beanName = mode + "HealthChecker";
        DeviceHealthChecker checker = checkerMap.get(beanName);
        if (checker == null) {
            throw new RuntimeException("checker not found");
        }
        return checker.isAlive(device);
    }
}
