package com.example.tool.device.controller;

import com.example.tool.device.service.WolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/device")
public class WolController {
    @Autowired
    private WolService wolService;

    @PostMapping("{id}/wake")
    public void wakeDevice(@PathVariable Integer id) {
        wolService.wakeDevice(id);
    }


}
