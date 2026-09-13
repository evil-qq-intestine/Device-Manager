package com.example.tool.device.controller;

import com.example.tool.device.entity.Device;
import com.example.tool.device.service.HeartbeatService;
import com.example.tool.user.util.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/heartbeat")
public class HeartbeatController {
    @Autowired
    private HeartbeatService heartbeatService;

    @PostMapping
    public Device heartbeat(@RequestParam String mac, @RequestHeader("X-Device-Token") String deviceToken) {
        return heartbeatService.updateHeartbeat(mac, deviceToken);
    }
}
