package com.example.tool.wake.service;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.exception.IdNotDetectedException;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.task.DeviceScheduledTasks;
import com.example.tool.wake.util.BeanCopyUtils;
import com.example.tool.wake.util.MacUtils;
import com.example.tool.wake.validator.DeviceValidator;
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
    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Autowired
    private DeviceValidator deviceValidator;

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

    public Device saveDevice(Device device) {
        MacUtils.checkMac(device.getMac());
        if(deviceRepository.existsByMac(device.getMac())) {
            throw new RuntimeException("设备MAC地址已存在：" + device.getMac());
        }
        Device savedDevice = deviceRepository.save(device);
        log.info("设备保存成功，ID：{}", savedDevice.getId());
        return savedDevice;
    }

    public Device updateDevice(Device device) {
        Device existingDevice = deviceRepository.findById(device.getId()).orElseThrow(() -> new RuntimeException("该设备ID不存在：" + device.getId()));
        deviceValidator.validateBeforeUpdate(device, existingDevice, deviceRepository);

        BeanCopyUtils.copyNonNullProperties(device, existingDevice);
        Device savedDevice = deviceRepository.save(existingDevice);
        log.info("设备信息更新成功：{}", savedDevice);
        return savedDevice;
    }

    public void deleteDevice(Integer id) {
        log.info("删除设备,id=：{}", id);
        Device existingDevice = deviceRepository.findById(id).orElseThrow(() -> new IdNotDetectedException("要删除的设备不存在，ID：" + id));
        DeviceScheduledTasks.removeLastPingMap(existingDevice.getId());
        deviceRepository.delete(existingDevice);
        log.info("删除设备成功,id=：{}", id);
    }
}
