package com.example.tool.versionController;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.system.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class UpdateService {

    @Value("${app.update.asset-prefix:device-manager}")
    private String assetPrefix;

    @Autowired
    private VersionService versionService;

    @Autowired
    private GithubReleaseClient githubReleaseClient;

    @Autowired
    private SystemConfigService systemConfigService;

    public synchronized String directUpdate() {
        VersionInfo info = versionService.getInfo();
        if (!info.isUpdateAvailable()) {
            throw new BusinessException("Already up to date");
        }
        if (!systemConfigService.getBoolean(SystemConfigService.DIRECT_UPDATE_ENABLED, false)) {
            throw new BusinessException("Direct update is disabled");
        }
        String mode = versionService.deploymentMode();
        if ("docker".equals(mode)) {
            throw new BusinessException("Direct update is not supported in Docker, please run the update command manually");
        }
        String assetName = assetName(mode);
        ReleaseAsset asset = versionService.getCachedAssets().stream()
                .filter(candidate -> assetName.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Release asset not found: " + assetName));
        Path target = currentExecutable(mode);
        if (target == null) {
            throw new BusinessException("Cannot locate the current application file");
        }
        try {
            Path temp = target.resolveSibling(target.getFileName() + ".new");
            githubReleaseClient.downloadTo(asset.getDownloadUrl(), temp);
            if (Files.size(temp) < 1024) {
                Files.deleteIfExists(temp);
                throw new BusinessException("Downloaded file looks invalid");
            }
            if ("native".equals(mode)) {
                temp.toFile().setExecutable(true, false);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Update failed: " + e.getMessage());
        }
        log.info("Updated {} -> {}, restarting process", info.getCurrent(), info.getLatest());
        Thread restart = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "device-manager-update-restart");
        restart.setDaemon(false);
        restart.start();
        return "Update downloaded. The process will exit now and the watchdog will start the new version.";
    }

    private String assetName(String mode) {
        if ("native".equals(mode)) {
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String suffix = (arch.contains("aarch64") || arch.contains("arm64")) ? "arm64" : "amd64";
            return assetPrefix + "-linux-" + suffix;
        }
        return assetPrefix + ".jar";
    }

    private Path currentExecutable(String mode) {
        if ("native".equals(mode)) {
            Optional<String> command = ProcessHandle.current().info().command();
            return command.map(value -> Path.of(value).toAbsolutePath()).orElse(null);
        }
        try {
            URI uri = UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
