package com.example.tool.device.service;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceMonitor;
import com.example.tool.device.entity.DeviceMonitorModeEnum;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.exception.IdNotDetectedException;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.repository.DeviceMonitorRepository;
import com.example.tool.device.task.DeviceScheduledTasks;
import com.example.tool.device.util.BeanCopyUtils;
import com.example.tool.device.validator.DeviceMonitorValidator;
import com.example.tool.device.validator.DeviceValidator;
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
    private DeviceValidator deviceValidator;

    @Autowired
    private DeviceMonitorValidator deviceMonitorValidator;

    @Transactional(readOnly = true)
    public List<Device> findAll() {
        log.info("查询设备列表");
        List<Device> devices = deviceRepository.findAll();
        log.info("共查询到{}条设备", devices);
        return devices;
    }

    @Transactional(readOnly = true)
    public Device findById(Integer id) {
        log.info("查询单个设备");
        return deviceRepository.findById(id).orElseThrow(() -> new IdNotDetectedException("未查询到设备"));
    }

    public DeviceMonitor findMonitorById(Integer id) {
        log.info("查询设备信息");
        return deviceMonitorRepository.findById(id).orElseThrow(() -> new IdNotDetectedException("未查询到设备信息"));
    }

    public Device saveDevice(Device device) {
        if(deviceRepository.existsByMac(device.getMac())) {
            throw new RuntimeException("设备MAC地址已存在：" + device.getMac());
        }
        if (device.getStatus() == null) {
            device.setStatus(DeviceStatusEnum.UNKNOWN);
        }
        Device savedDevice = deviceRepository.save(device);

        DeviceMonitor.DeviceMonitorBuilder builder = DeviceMonitor.builder()
                .status(DeviceStatusEnum.UNKNOWN)
                .lastOnlineTime(null);

        if (device.getMonitorMode() != null) {
            builder.monitorMode(device.getMonitorMode());
        } else {
            builder.monitorMode(DeviceMonitorModeEnum.PING);
        }
        DeviceMonitor deviceMonitor = builder.build();
        deviceMonitorRepository.save(deviceMonitor);
        log.info("设备保存成功，ID：{}", savedDevice.getDeviceId());
        return savedDevice;
    }

    public Device updateDevice(Device device) {
        Device newDevice = deviceRepository.findById(device.getDeviceId()).orElseThrow(() -> new RuntimeException("该设备ID不存在：" + device.getDeviceId()));
        deviceValidator.validateBeforeUpdate(device);

        BeanCopyUtils.copyNonNullProperties(device, newDevice);
        Device savedDevice = deviceRepository.save(newDevice);
        log.info("设备基础信息更新成功：{}", savedDevice);
        return savedDevice;
    }

    public DeviceMonitor updateDeviceMonitor(DeviceMonitor deviceMonitor) {
        DeviceMonitor newDeviceMonitor = deviceMonitorRepository.findById(deviceMonitor.getMonitorId()).orElseThrow(() -> new RuntimeException("监控配置不存在：" + deviceMonitor.getMonitorId()));
        deviceMonitorValidator.validateBeforeUpdate(deviceMonitor);

        BeanCopyUtils.copyNonNullProperties(deviceMonitor, newDeviceMonitor);
        DeviceMonitor savedDeviceMonitor = deviceMonitorRepository.save(newDeviceMonitor);
        log.info("设备状态信息更新成功：{}", savedDeviceMonitor);

        return newDeviceMonitor;
    }

    public void deleteDevice(Integer id) {
        log.info("删除设备,id=：{}", id);
        Device existingDevice = deviceRepository.findById(id).orElseThrow(() -> new IdNotDetectedException("要删除的设备不存在，ID：" + id));
        DeviceScheduledTasks.removeLastPingMap(existingDevice.getDeviceId());
        // 直接删 Device，Monitor 会被级联删掉（因为 cascade = ALL）
        deviceRepository.delete(existingDevice);
        log.info("删除设备成功,id=：{}", id);
    }
}
