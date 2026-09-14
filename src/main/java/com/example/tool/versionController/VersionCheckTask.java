package com.example.tool.versionController;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VersionCheckTask {

    @Autowired
    private VersionService versionService;

    @Scheduled(initialDelayString = "${app.update.initial-delay-ms:15000}",
            fixedDelayString = "${app.update.check-interval-ms:21600000}")
    public void checkForUpdates() {
        VersionInfo info = versionService.refresh(true);
        if (info.isUpdateAvailable()) {
            log.info("New version available: {} (current {})", info.getLatest(), info.getCurrent());
        }
    }
}
