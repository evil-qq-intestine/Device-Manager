package com.example.tool.device.controller;

import com.example.tool.device.entity.Device;
import com.example.tool.device.service.HeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/heartbeat")
public class HeartbeatController {
    @Autowired
    private HeartbeatService heartbeatService;

    @PostMapping
    public Device heartbeat(@RequestParam String mac) {
        return heartbeatService.updateHeartbeat(mac);
    }

}
