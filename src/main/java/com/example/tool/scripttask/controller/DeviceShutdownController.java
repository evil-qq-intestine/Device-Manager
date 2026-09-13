package com.example.tool.scripttask.controller;

import com.example.tool.scripttask.request.DeviceSshRequest;
import com.example.tool.scripttask.response.DeviceSshResponse;
import com.example.tool.scripttask.service.DeviceShutdownService;
import com.example.tool.user.util.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备级 SSH 配置与独立关机（不依赖脚本任务）。
 */
@RestController
@RequestMapping("/api/device")
public class DeviceShutdownController {

    private final DeviceShutdownService deviceShutdownService;

    public DeviceShutdownController(DeviceShutdownService deviceShutdownService) {
        this.deviceShutdownService = deviceShutdownService;
    }

    @GetMapping("/{deviceId}/ssh")
    public DeviceSshResponse getSsh(@PathVariable Integer deviceId,
                                    @AuthenticationPrincipal CustomUserDetails user) {
        return deviceShutdownService.getSshConfig(deviceId, user.getUserId());
    }

    @PutMapping("/{deviceId}/ssh")
    public DeviceSshResponse setSsh(@PathVariable Integer deviceId,
                                    @Valid @RequestBody DeviceSshRequest request,
                                    @AuthenticationPrincipal CustomUserDetails user) {
        return deviceShutdownService.setSshConfig(deviceId, user.getUserId(), request);
    }

    @PostMapping("/{deviceId}/shutdown")
    public Map<String, Object> shutdown(@PathVariable Integer deviceId,
                                        @AuthenticationPrincipal CustomUserDetails user) {
        return deviceShutdownService.shutdownNow(deviceId, user.getUserId());
    }
}
