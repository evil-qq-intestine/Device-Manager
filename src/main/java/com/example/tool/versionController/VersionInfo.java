package com.example.tool.versionController;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VersionInfo {

    private String current;
    private String latest;
    private boolean updateAvailable;
    private String releaseUrl;
    private String releaseNotes;
    private String publishedAt;
    private String updateCommand;
    private boolean directUpdateEnabled;
    private boolean directUpdateSupported;
    private String deploymentMode;
    private String checkedAt;
    private String error;
}
