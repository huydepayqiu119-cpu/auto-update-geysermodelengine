package com.kaito.autoupdate.core;

import java.util.List;

public record ReleaseInfo(String tag, String htmlUrl, List<ReleaseAsset> assets) {
    public record ReleaseAsset(String name, String downloadUrl) {}
}
