package com.example.tool.device.service;


import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceIpMode;
import com.example.tool.device.entity.DeviceStatus;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.service.checker.DeviceIpChecker;
import com.example.tool.device.util.MacUtils;
import com.example.tool.device.service.component.MagicPacketComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class WolService {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MagicPacketComponent magicPacketComponent;

    @Autowired
    private Map<String, DeviceIpChecker> ipCheckerMap;

    public void wakeDevice(Integer deviceId) {
        if (deviceId == null) {
            throw new BusinessException("deviceId is null");
        }
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new RuntimeException("device not found"));
        boolean sendSuccess = ipStrategy(device, MacUtils.parse(device.getMac()));
        if (!sendSuccess) {
            throw new BusinessException("device send magic packet failed");
        }
        device.setProbeStartTime(LocalDateTime.now());
        device.setStatus(DeviceStatus.PROBE);
        deviceRepository.save(device);
    }

    private boolean ipStrategy(Device device, byte[] payload) {
        if (device.getIpMode() == null) {
            log.error("ip mode is null,id:{}", device.getDeviceId());
            throw new BusinessException("ip mode is null");
        } else if (device.getIpMode() == DeviceIpMode.IPV4) {
            return ipSendChecker(device, payload, 4);
        } else if (device.getIpMode() == DeviceIpMode.IPV6) {
            return ipSendChecker(device, payload, 6);
        } else {
            throw new BusinessException("ip mode is unknown");
        }
    }

    private boolean ipSendChecker(Device device, byte[] payload, int ipGrade) {
        String key = "deviceIpv" + ipGrade + "Checker";
        if (ipCheckerMap.get(key) == null) {
            log.error("{} checkerMap is null, ID : {}", key, device.getDeviceId());
            return false;
        }
        try {
            magicPacketComponent.sendMagicPacket(payload, ipCheckerMap.get("deviceIpv" + ipGrade + "Checker").resolveDestinationAddress(device.getDeviceId()));
            return true;
        } catch (Exception e) {
            log.error("send magic packet error", e);
            return false;
        }
    }
}
