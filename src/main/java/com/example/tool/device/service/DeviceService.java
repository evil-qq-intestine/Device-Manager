package com.example.tool.device.service;

import com.example.tool.device.entity.*;
import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.exception.IdNotDetectedException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.repository.DeviceMonitorRepository;
import com.example.tool.device.script.ScriptForClient;
import com.example.tool.device.task.DeviceScheduledTasks;
import com.example.tool.device.validator.DeviceMonitorValidator;
import com.example.tool.device.validator.DeviceValidator;
import com.example.tool.scripttask.service.ScriptTaskService;
import com.example.tool.user.entity.User;
import com.example.tool.user.reopsitory.UserRepository;
import com.example.tool.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceMonitorRepository deviceMonitorRepository;
    private final DeviceMonitorValidator deviceMonitorValidator;
    private final DeviceValidator deviceValidator;
    private final UserRepository userRepository;
    private final ScriptTaskService scriptTaskService;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceMonitorRepository deviceMonitorRepository,
                         DeviceMonitorValidator deviceMonitorValidator,
                         DeviceValidator deviceValidator,
                         UserRepository userRepository,
                         ScriptTaskService scriptTaskService) {
        this.deviceRepository = deviceRepository;
        this.deviceMonitorRepository = deviceMonitorRepository;
        this.deviceMonitorValidator = deviceMonitorValidator;
        this.deviceValidator = deviceValidator;
        this.userRepository = userRepository;
        this.scriptTaskService = scriptTaskService;
    }

    @Value("${app.server-url}")
    private String serverAddress;

    @Transactional(readOnly = true)
    public List<Device> findDevicesByUserId(Integer userId) {
        log.info("Query device list");
        List<Device> devices = deviceRepository.findByUserId(userId);
        log.info("Found {} device(s)", devices.size());
        return devices;
    }

    @Transactional(readOnly = true)
    public Device findById(Integer deviceId, Integer userId) {
        log.info("User {} queries a single device", userId);

        return deviceRepository.findByDeviceIdAndUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("Device not found"));
    }

    @Transactional(readOnly = true)
    public List<DeviceMonitor> findAllDeviceMonitor(Integer userId) {
        log.info("Query all device status info");
        return deviceMonitorRepository.findByDeviceUserId(userId);
    }

    @Transactional(readOnly = true)
    public DeviceMonitor findMonitorById(Integer deviceId, Integer userId) {
        log.info("Query device info");
        return deviceMonitorRepository.findByMonitorIdAndDeviceUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("Device monitor info not found"));
    }

    public Device saveDevice(Device device, Integer userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException("User not found, id: " + userId));
        device.setUser(user);

        deviceValidator.validateBeforeSave(device);
        device.ensureMonitor();
        deviceMonitorValidator.validateBeforeUpdate(device.getMonitor());

        Device savedDevice = deviceRepository.save(device);

        log.info("Device saved, id: {}", savedDevice.getDeviceId());
        return savedDevice;
    }

    public Device updateDevice(Device device, Integer userId) {
        Device newDevice = deviceRepository.findByDeviceIdAndUserId(device.getDeviceId(), userId).orElseThrow(() -> new IdNotDetectedException("Device not found, id: " + device.getDeviceId()));
        deviceValidator.validateBeforeUpdate(device, newDevice);

        // 只更新基础字段，避免 Device 上委托 monitor 的计算型 getter 把监控配置覆盖成默认值
        if (device.getMac() != null) {
            newDevice.setMac(device.getMac());
        }
        if (device.getIp() != null) {
            newDevice.setIp(device.getIp());
        }
        if (device.getDeviceName() != null) {
            newDevice.setDeviceName(device.getDeviceName());
        }

        // IP 变化时，监控的 IP 协议始终以新 IP 为准
        newDevice.ensureMonitor();
        newDevice.getMonitor().setIpMode(DeviceIpModeEnum.getIpMode(newDevice.getIp()));
        Device savedDevice = deviceRepository.save(newDevice);
        log.info("Device basic info updated: {}", savedDevice);
        return savedDevice;
    }

    public DeviceMonitor updateDeviceMonitor(DeviceMonitor deviceMonitor, Integer userId) {
        DeviceMonitor newDeviceMonitor = deviceMonitorRepository.findByMonitorIdAndDeviceUserId(deviceMonitor.getMonitorId(), userId).orElseThrow(() -> new IdNotDetectedException("Device monitor config not found, id: " + deviceMonitor.getMonitorId()));
        deviceMonitorValidator.validateBeforeUpdate(deviceMonitor);

        // 只更新可配置字段，避免客户端篡改 status/lastOnlineTime/probeStartTime 等运行时字段
        if (deviceMonitor.getMonitorMode() != null) {
            newDeviceMonitor.setMonitorMode(deviceMonitor.getMonitorMode());
        }
        if (deviceMonitor.getPingInterval() != null) {
            newDeviceMonitor.setPingInterval(deviceMonitor.getPingInterval());
        }
        if (deviceMonitor.getPingTimeout() != null) {
            newDeviceMonitor.setPingTimeout(deviceMonitor.getPingTimeout());
        }
        if (deviceMonitor.getResponseTimeout() != null) {
            newDeviceMonitor.setResponseTimeout(deviceMonitor.getResponseTimeout());
        }
        if (deviceMonitor.getWakeTimeout() != null) {
            newDeviceMonitor.setWakeTimeout(deviceMonitor.getWakeTimeout());
        }
        // IP 协议由设备地址自动决定，忽略客户端传入值
        if (newDeviceMonitor.getDevice() != null) {
            newDeviceMonitor.setIpMode(DeviceIpModeEnum.getIpMode(newDeviceMonitor.getDevice().getIp()));
        }
        DeviceMonitor savedDeviceMonitor = deviceMonitorRepository.save(newDeviceMonitor);
        log.info("Device status info updated: {}", savedDeviceMonitor);

        return savedDeviceMonitor;
    }

    public void deleteDevice(Integer deviceId, Integer userId) {
        log.info("Delete device, deviceId: {}", deviceId);
        Device existingDevice = deviceRepository.findByDeviceIdAndUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("Device to delete not found, id: " + deviceId));
        DeviceScheduledTasks.removeLastPingMap(List.of(existingDevice));
        scriptTaskService.detachDevice(deviceId);
        deviceRepository.delete(existingDevice);
        log.info("Device deleted, deviceId: {}, userId: {}", deviceId, userId);
    }

    @Transactional(readOnly = true)
    public String generateHeartbeatScript(Integer deviceId, Integer userId, String os) {
        Device device = deviceRepository.findByDeviceIdAndUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("Device not found or no access"));

        ScriptForClient client = ScriptForClient.fromString(os);
        return client.render(serverAddress, device.getMac(), device.getDeviceId(), device.getDeviceToken());
    }
}
