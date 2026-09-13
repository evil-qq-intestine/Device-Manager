package com.example.tool.device.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.util.MacUtils;
import com.example.tool.scripttask.service.DeviceOnlineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class HeartbeatService {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public Device updateHeartbeat(String mac, String deviceToken) {
        MacUtils.checkMac(mac);

        Device device = deviceRepository.findByMacAndDeviceToken(mac, deviceToken)
                .orElseThrow(() -> new BusinessException("Device not found, MAC: " + mac));

        DeviceStatusEnum previous = device.getStatus();
        device.setLastOnlineTime(LocalDateTime.now());
        device.setStatus(DeviceStatusEnum.ONLINE);
        Device saved = deviceRepository.save(device);

        if (previous != DeviceStatusEnum.ONLINE) {
            eventPublisher.publishEvent(new DeviceOnlineEvent(saved.getDeviceId()));
        }
        return saved;
    }
}
