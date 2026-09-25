package com.example.tool.versionController;

import com.example.tool.system.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VersionService {

    @Value("${app.version:dev}")
    private String currentVersion;

    @Value("${app.update.github-repo:evil-qq-intestine/Device-Manager}")
    private String githubRepo;

    @Value("${app.update.check-interval-ms:21600000}")
    private long checkIntervalMs;

    @Autowired
    private GithubReleaseClient githubReleaseClient;

    @Autowired
    private SystemConfigService systemConfigService;

    private volatile VersionInfo cached;
    private volatile List<ReleaseAsset> cachedAssets = List.of();
    private volatile long lastCheckAt;

    public String getCurrentVersion() {
        return currentVersion;
    }

    public VersionInfo getInfo() {
        VersionInfo info = cached;
        return info != null ? info : placeholder();
    }

    public synchronized VersionInfo refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && cached != null && (now - lastCheckAt) < checkIntervalMs) {
            return cached;
        }
        try {
            JsonNode release = githubReleaseClient.fetchLatestRelease(githubRepo);
            String latest = normalize(release.path("tag_name").asText(""));
            List<ReleaseAsset> assets = new ArrayList<>();
            for (JsonNode asset : release.path("assets")) {
                assets.add(new ReleaseAsset(
                        asset.path("name").asText(""),
                        asset.path("browser_download_url").asText(""),
                        asset.path("size").asLong(0)));
            }
            cachedAssets = List.copyOf(assets);
            cached = build(latest,
                    release.path("html_url").asText(""),
                    release.path("body").asText(""),
                    release.path("published_at").asText(""),
                    null);
        } catch (Exception e) {
            log.warn("Version check failed: {}", e.getMessage());
            if (cached == null) {
                cached = build(null, null, null, null, e.getMessage());
            } else {
                cached.setError(e.getMessage());
            }
        }
        lastCheckAt = now;
        return cached;
    }

    public synchronized VersionInfo applySettings() {
        if (cached != null) {
            applyDirectUpdateFlags(cached);
        }
        return getInfo();
    }

    public List<ReleaseAsset> getCachedAssets() {
        return cachedAssets;
    }

    private VersionInfo placeholder() {
        VersionInfo info = new VersionInfo();
        info.setCurrent(currentVersion);
        info.setDirectUpdateEnabled(systemConfigService.getBoolean(SystemConfigService.DIRECT_UPDATE_ENABLED, false));
        return info;
    }

    private VersionInfo build(String latest, String releaseUrl, String releaseNotes,
                              String publishedAt, String error) {
        VersionInfo info = new VersionInfo();
        info.setCurrent(currentVersion);
        info.setLatest(latest);
        info.setReleaseUrl(releaseUrl);
        info.setReleaseNotes(releaseNotes);
        info.setPublishedAt(publishedAt);
        info.setCheckedAt(Instant.now().toString());
        info.setError(error);
        info.setUpdateAvailable(latest != null && compareVersions(latest, currentVersion) > 0);
        applyDirectUpdateFlags(info);
        return info;
    }

    private void applyDirectUpdateFlags(VersionInfo info) {
        boolean enabled = systemConfigService.getBoolean(SystemConfigService.DIRECT_UPDATE_ENABLED, false);
        info.setDirectUpdateEnabled(enabled);
        info.setDirectUpdateSupported(enabled && info.isUpdateAvailable());
    }

    public static String normalize(String tag) {
        if (tag == null) {
            return null;
        }
        String version = tag.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        return version.isEmpty() ? null : version;
    }

    public static int compareVersions(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(a[i], b[i]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        int[] result = new int[3];
        if (version == null) {
            return result;
        }
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        int plus = value.indexOf('+');
        if (plus >= 0) {
            value = value.substring(0, plus);
        }
        String[] parts = value.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try {
                result[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}
