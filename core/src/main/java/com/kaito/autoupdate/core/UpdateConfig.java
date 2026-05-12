package com.kaito.autoupdate.core;

public record UpdateConfig(
    String githubOwner,
    String githubRepo,
    boolean notifyOnStartup,
    boolean autoUpdateOnStartup,
    int autoUpdateIntervalMinutes,
    String targetJarPath,
    String targetMcpackPath,
    String requiredJarSuffix,
    String requiredMcpackSuffix,
    String mcpackHeaderName
) {
    public static UpdateConfig defaults() {
        return new UpdateConfig(
            "GeyserExtensionists",
            "GeyserDisplayEntity",
            true,
            true,
            60,
            "Geyser/extensions/DisplayEntity.jar",
            "Geyser/packs/DisplayEntityPack.mcpack",
            ".jar",
            ".mcpack",
            "§fDisplay Entity Pack (§aDistributor Discord Kaito-@_jaysonmalon)"
        );
    }
}
