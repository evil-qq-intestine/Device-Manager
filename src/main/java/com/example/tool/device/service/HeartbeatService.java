package com.example.tool.device.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.util.MacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class HeartbeatService {
    @Autowired
    private DeviceRepository deviceRepository;

    @Transactional
    public Device updateHeartbeat(String mac, Integer userId) {
        MacUtils.checkMac(mac);

        Device device = deviceRepository.findByMacAndUserId(mac, userId)
                .orElseThrow(() -> new BusinessException("设备未找到，MAC: " + mac));

        device.setLastOnlineTime(LocalDateTime.now());
        device.setStatus(DeviceStatusEnum.ONLINE);
        return deviceRepository.save(device);
    }
}
