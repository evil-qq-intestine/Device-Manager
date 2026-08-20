package com.example.tool.wake.controller;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.service.HeartbeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
