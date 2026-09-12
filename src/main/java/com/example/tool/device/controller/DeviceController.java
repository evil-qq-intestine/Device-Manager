package com.example.tool.device.controller;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceMonitor;
import com.example.tool.device.service.DeviceService;
import com.example.tool.user.util.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/device")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping("")
    public List<Device> findAll(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return deviceService.getDevicesByUserId(userDetails.getUserId());
    }

    @GetMapping("/{id}")
    public Device findById(@PathVariable Integer deviceId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return deviceService.findById(deviceId, userDetails.getUserId());
    }

    @GetMapping("/monitor")
    public List<DeviceMonitor> findAllMonitor(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return deviceService.findAllDeviceMonitor(userDetails.getUserId());
    }

    @GetMapping("/monitor/{deviceMonitorId}")
    public DeviceMonitor findMonitorById(@PathVariable Integer deviceMonitorId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return deviceService.findMonitorById(deviceMonitorId, userDetails.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Device addDevice(@Valid @RequestBody Device device, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return deviceService.saveDevice(device, userDetails.getUserId());
    }

    @PutMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Device updateDevice(@PathVariable Integer deviceId, @Valid @RequestBody Device device, @AuthenticationPrincipal CustomUserDetails userDetails) {
        device.setDeviceId(deviceId);
        return deviceService.updateDevice(device, userDetails.getUserId());
    }

    @PutMapping("/monitor/{deviceMonitorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public DeviceMonitor monitorDevice(@PathVariable Integer deviceMonitorId, @Valid @RequestBody DeviceMonitor deviceMonitor, @AuthenticationPrincipal CustomUserDetails userDetails) {
        deviceMonitor.setMonitorId(deviceMonitorId);
        return deviceService.updateDeviceMonitor(deviceMonitor, userDetails.getUserId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable Integer id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        deviceService.deleteDevice(id, userDetails.getUserId());
    }
}
