package com.example.tool.device.service;

import com.example.tool.device.entity.*;
import com.example.tool.device.exception.IdNotDetectedException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.repository.DeviceMonitorRepository;
import com.example.tool.device.task.DeviceScheduledTasks;
import com.example.tool.device.util.BeanCopyUtils;
import com.example.tool.device.validator.DeviceMonitorValidator;
import com.example.tool.device.validator.DeviceValidator;
import com.example.tool.user.entity.User;
import com.example.tool.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional
public class DeviceService {

    //private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final DeviceMonitorRepository deviceMonitorRepository;

    public DeviceService(DeviceRepository deviceRepository, DeviceMonitorRepository deviceMonitorRepository) {
        this.deviceRepository = deviceRepository;
        this.deviceMonitorRepository = deviceMonitorRepository;
    }

    @Autowired
    private UserService userService;

    @Autowired
    private DeviceValidator deviceValidator;

    @Autowired
    private DeviceMonitorValidator deviceMonitorValidator;

    @Transactional(readOnly = true)
    public List<Device> findDevicesByUserId(Integer userId) {
        log.info("查询设备列表");
        List<Device> devices = deviceRepository.findByUserId(userId);
        log.info("共查询到{}条设备", devices.size());
        return devices;
    }

    @Transactional(readOnly = true)
    public Device findById(Integer deviceId, Integer userId) {
        log.info("用户{}查询单个设备", userId);

        return deviceRepository.findByDeviceIdAndUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("未查询到设备"));
    }

    @Transactional(readOnly = true)
    public List<DeviceMonitor> findAllDeviceMonitor(Integer userId) {
        log.info("查询所有设备状态信息");
        return deviceMonitorRepository.findByDeviceUserId(userId);
    }

    @Transactional(readOnly = true)
    public DeviceMonitor findMonitorById(Integer deviceId, Integer userId) {
        log.info("查询设备信息");
        return deviceMonitorRepository.findByMonitorIdAndDeviceUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("未查询到设备信息"));
    }

    public Device saveDevice(Device device, Integer userId) {
        User user = userService.getUserById(userId);
        device.setUser(user);

        deviceValidator.validateBeforeSave(device);
        device.ensureMonitor();
        deviceMonitorValidator.validateBeforeUpdate(device.getMonitor());

        Device savedDevice = deviceRepository.save(device);

        log.info("设备保存成功，ID：{}", savedDevice.getDeviceId());
        return savedDevice;
    }

    public Device updateDevice(Device device, Integer userId) {
        Device newDevice = deviceRepository.findByDeviceIdAndUserId(device.getDeviceId(), userId).orElseThrow(() -> new RuntimeException("该设备ID不存在：" + device.getDeviceId()));
        deviceValidator.validateBeforeSave(device, newDevice);

        BeanCopyUtils.copyNonNullProperties(device, newDevice);
        Device savedDevice = deviceRepository.save(newDevice);
        log.info("设备基础信息更新成功：{}", savedDevice);
        return savedDevice;
    }

    public DeviceMonitor updateDeviceMonitor(DeviceMonitor deviceMonitor, Integer userId) {
        DeviceMonitor newDeviceMonitor = deviceMonitorRepository.findByMonitorIdAndDeviceUserId(deviceMonitor.getMonitorId(), userId).orElseThrow(() -> new RuntimeException("监控配置不存在：" + deviceMonitor.getMonitorId()));
        deviceMonitorValidator.validateBeforeUpdate(deviceMonitor);

        BeanCopyUtils.copyNonNullProperties(deviceMonitor, newDeviceMonitor);
        DeviceMonitor savedDeviceMonitor = deviceMonitorRepository.save(newDeviceMonitor);
        log.info("设备状态信息更新成功：{}", savedDeviceMonitor);

        return savedDeviceMonitor;
    }

    public void deleteDevice(Integer deviceId, Integer userId) {
        log.info("删除设备,deviceId=：{}", deviceId);
        Device existingDevice = deviceRepository.findByDeviceIdAndUserId(deviceId, userId).orElseThrow(() -> new IdNotDetectedException("要删除的设备不存在，ID：" + deviceId));
        DeviceScheduledTasks.removeLastPingMap(List.of(existingDevice));
        // 直接删 Device，Monitor 会被级联删掉（因为 cascade = ALL）
        deviceRepository.delete(existingDevice);
        log.info("删除设备成功,deviceId : {},userId : {}", deviceId, userId);
    }
}
