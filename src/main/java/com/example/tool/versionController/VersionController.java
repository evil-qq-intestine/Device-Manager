package com.example.tool.versionController;

import com.example.tool.system.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VersionController {

    @Autowired
    private VersionService versionService;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping("/version")
    public VersionInfo version() {
        return versionService.getInfo();
    }

    @PostMapping("/version/check")
    @PreAuthorize("hasRole('ADMIN')")
    public VersionInfo check() {
        return versionService.refresh(true);
    }

    @PostMapping("/version/update")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", updateService.directUpdate());
        return body;
    }

    @GetMapping("/version/watchdog")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> watchdog() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/x-shellscript; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"device-manager-watchdog.sh\"")
                .body(WatchdogScript.linux());
    }

    @GetMapping("/version/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getSettings() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("directUpdateEnabled",
                systemConfigService.getBoolean(SystemConfigService.DIRECT_UPDATE_ENABLED, false));
        return body;
    }

    @PutMapping("/version/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> setSettings(@RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(request.get("directUpdateEnabled")));
        systemConfigService.setBoolean(SystemConfigService.DIRECT_UPDATE_ENABLED, enabled);
        versionService.applySettings();
        return getSettings();
    }
}
