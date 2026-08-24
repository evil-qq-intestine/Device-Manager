package com.example.tool.device.controller;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceMonitor;
import com.example.tool.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping("")
    public List<Device> findAll() {
        return deviceService.findAll();
    }

    @GetMapping("/{id}")
    public Device findById(@PathVariable Integer id) {
        return deviceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Device addDevice(@Valid @RequestBody Device device) {
        return deviceService.saveDevice(device);
    }

    @PutMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Device updateDevice(@PathVariable Integer deviceId, @Valid @RequestBody Device device) {
        device.setDeviceId(deviceId);
        return deviceService.updateDevice(device);
    }

    @PutMapping("/monitor/{deviceMonitorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public DeviceMonitor monitorDevice(@PathVariable Integer deviceMonitorId, @Valid @RequestBody DeviceMonitor deviceMonitor) {
        deviceMonitor.setMonitorId(deviceMonitorId);
        return deviceService.updateDeviceMonitor(deviceMonitor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable Integer id) {
        deviceService.deleteDevice(id);
    }
}
