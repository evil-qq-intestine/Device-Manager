package com.example.tool.versionController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VersionController {

    private static final String VERSION = "1.0.0";

    @GetMapping("/version")
    public String version(){
        return VERSION;
    }
}
