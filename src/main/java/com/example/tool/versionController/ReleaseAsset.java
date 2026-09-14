package com.example.tool.versionController;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReleaseAsset {

    private final String name;
    private final String downloadUrl;
    private final long size;
}
