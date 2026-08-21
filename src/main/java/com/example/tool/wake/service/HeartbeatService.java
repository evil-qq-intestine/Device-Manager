package com.example.tool.wake.service;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.entity.DeviceStatus;
import com.example.tool.wake.exception.BusinessException;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.util.MacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class HeartbeatService {
    @Autowired
    private DeviceRepository deviceRepository;

    public Device updateHeartbeat(String mac) {
        MacUtils.checkMac(mac);

        Device device = deviceRepository.findByMac(mac)
                .orElseThrow(() -> new BusinessException("设备未找到，MAC: " + mac));

        device.setLastOnlineTime(LocalDateTime.now());
        device.setStatus(DeviceStatus.ONLINE);
        return deviceRepository.save(device);
    }
}
