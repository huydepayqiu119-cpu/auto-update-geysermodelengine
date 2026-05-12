package com.kaito.autoupdate.core;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public class Updater {
    public static final String AUTHOR = "Kaito";

    private final GithubReleaseClient releaseClient;
    private final FileDownloader downloader;
    private final McpackRepacker repacker;

    public Updater(HttpClient httpClient, String userAgent) {
        this.releaseClient = new GithubReleaseClient(httpClient, userAgent);
        this.downloader = new FileDownloader(httpClient, userAgent);
        this.repacker = new McpackRepacker();
    }

    public UpdateSummary checkAndMaybeUpdateAll(UpdateConfig cfg, Path pluginsDir, Path dataDir, boolean doUpdate) throws IOException {
        List<String> available = new ArrayList<>();
        List<String> updated = new ArrayList<>();

        available.addAll(checkDisplayEntity(cfg, pluginsDir, dataDir, doUpdate, updated));
        available.addAll(checkGeyserModelEngine(pluginsDir, dataDir, doUpdate, updated));
        available.addAll(checkGeyserUtils(pluginsDir, dataDir, doUpdate, updated));

        boolean hasAny = !available.isEmpty();
        return new UpdateSummary(hasAny, available, updated);
    }

    private List<String> checkDisplayEntity(UpdateConfig cfg, Path pluginsDir, Path dataDir, boolean doUpdate, List<String> updated) throws IOException {
        String installedTag = readInstalledTag(dataDir, "last-displayentity.txt");
        ReleaseInfo release = releaseClient.fetchFirstRelease(cfg.githubOwner(), cfg.githubRepo());

        String latestTag = normalizeTag(release.tag());
        boolean hasUpdate = !latestTag.equals(normalizeTag(installedTag));

        if (!hasUpdate) return List.of();

        List<String> available = new ArrayList<>();
        available.add("DisplayEntity " + latestTag + " " + release.htmlUrl());

        if (!doUpdate) return available;

        Path jarTarget = pluginsDir.resolve(cfg.targetJarPath());
        Path mcpackTarget = pluginsDir.resolve(cfg.targetMcpackPath());

        ReleaseInfo.ReleaseAsset jar = findAsset(release, cfg.requiredJarSuffix());
        ReleaseInfo.ReleaseAsset mcpack = findAsset(release, cfg.requiredMcpackSuffix());

        downloader.downloadTo(jar.downloadUrl(), jarTarget);

        Path tempMcpack = Files.createTempFile("displayentity-pack-", ".mcpack");
        try {
            downloader.downloadTo(mcpack.downloadUrl(), tempMcpack);
            repacker.repack(tempMcpack, mcpackTarget, cfg.mcpackHeaderName());
        } finally {
            try {
                Files.deleteIfExists(tempMcpack);
            } catch (IOException ignored) {}
        }

        writeInstalledTag(dataDir, "last-displayentity.txt", latestTag);
        updated.add(jarTarget.toString());
        updated.add(mcpackTarget.toString());
        return available;
    }

    private List<String> checkGeyserModelEngine(Path pluginsDir, Path dataDir, boolean doUpdate, List<String> updated) throws IOException {
        String installedTag = readInstalledTag(dataDir, "last-geysermodelengine.txt");
        ReleaseInfo release = releaseClient.fetchFirstRelease("GeyserExtensionists", "GeyserModelEngine");
        String latestTag = normalizeTag(release.tag());
        boolean hasUpdate = !latestTag.equals(normalizeTag(installedTag));
        if (!hasUpdate) return List.of();

        List<String> available = new ArrayList<>();
        available.add("GeyserModelEngine " + latestTag + " " + release.htmlUrl());
        if (!doUpdate) return available;

        ReleaseInfo.ReleaseAsset engineJar = findAssetByContains(release, ".jar", "GeyserModelEngine");
        ReleaseInfo.ReleaseAsset extJar = findAssetByContains(release, ".jar", "Extension");

        deleteMatching(pluginsDir, "GeyserModelEngine*.jar");
        downloader.downloadTo(engineJar.downloadUrl(), pluginsDir.resolve(engineJar.name()));
        updated.add(pluginsDir.resolve(engineJar.name()).toString());

        Path geyserDir = resolveGeyserDir(pluginsDir);
        Path extensionsDir = geyserDir.resolve("extensions");
        Files.createDirectories(extensionsDir);
        deleteMatching(extensionsDir, "GeyserModelEngineExtension*.jar");
        downloader.downloadTo(extJar.downloadUrl(), extensionsDir.resolve(extJar.name()));
        updated.add(extensionsDir.resolve(extJar.name()).toString());

        writeInstalledTag(dataDir, "last-geysermodelengine.txt", latestTag);
        return available;
    }

    private ReleaseInfo.ReleaseAsset findAsset(ReleaseInfo release, String suffix) throws IOException {
        Optional<ReleaseInfo.ReleaseAsset> asset = release.assets().stream()
            .filter(a -> a.name() != null && a.name().endsWith(suffix))
            .findFirst();
        if (asset.isEmpty()) throw new IOException("missing asset " + suffix);
        return asset.get();
    }

    private ReleaseInfo.ReleaseAsset findAssetByContains(ReleaseInfo release, String suffix, String contains) throws IOException {
        Optional<ReleaseInfo.ReleaseAsset> asset = release.assets().stream()
            .filter(a -> a.name() != null && a.name().endsWith(suffix) && a.name().contains(contains))
            .findFirst();
        if (asset.isEmpty()) throw new IOException("missing asset " + contains);
        return asset.get();
    }

    private List<String> checkGeyserUtils(Path pluginsDir, Path dataDir, boolean doUpdate, List<String> updated) throws IOException {
        String installedTag = readInstalledTag(dataDir, "last-geyserutils.txt");
        ReleaseInfo release = releaseClient.fetchFirstRelease("GeyserExtensionists", "GeyserUtils");
        String latestTag = normalizeTag(release.tag());
        boolean hasUpdate = !latestTag.equals(normalizeTag(installedTag));
        if (!hasUpdate) return List.of();

        List<String> available = new ArrayList<>();
        available.add("GeyserUtils " + latestTag + " " + release.htmlUrl());
        if (!doUpdate) return available;

        ReleaseInfo.ReleaseAsset spigotJar = findAssetByContains(release, ".jar", "spigot");
        ReleaseInfo.ReleaseAsset bungeeJar = findAssetByContains(release, ".jar", "bungee");
        ReleaseInfo.ReleaseAsset velocityJar = findAssetByContains(release, ".jar", "velocity");
        ReleaseInfo.ReleaseAsset geyserJar = findAssetByContains(release, ".jar", "geyser");

        deleteMatching(pluginsDir, "geyserutils-spigot*.jar");
        downloader.downloadTo(spigotJar.downloadUrl(), pluginsDir.resolve(spigotJar.name()));
        updated.add(pluginsDir.resolve(spigotJar.name()).toString());

        Path bungeeDir = pluginsDir.resolve("BungeeCord");
        Files.createDirectories(bungeeDir);
        deleteMatching(bungeeDir, "geyserutils-bungee*.jar");
        downloader.downloadTo(bungeeJar.downloadUrl(), bungeeDir.resolve(bungeeJar.name()));
        updated.add(bungeeDir.resolve(bungeeJar.name()).toString());

        Path velocityDir = pluginsDir.resolve("Velocity");
        Files.createDirectories(velocityDir);
        deleteMatching(velocityDir, "geyserutils-velocity*.jar");
        downloader.downloadTo(velocityJar.downloadUrl(), velocityDir.resolve(velocityJar.name()));
        updated.add(velocityDir.resolve(velocityJar.name()).toString());

        Path geyserDir = resolveGeyserDir(pluginsDir);
        Path extensionsDir = geyserDir.resolve("extensions");
        Files.createDirectories(extensionsDir);
        deleteMatching(extensionsDir, "geyserutils-geyser*.jar");
        downloader.downloadTo(geyserJar.downloadUrl(), extensionsDir.resolve(geyserJar.name()));
        updated.add(extensionsDir.resolve(geyserJar.name()).toString());

        writeInstalledTag(dataDir, "last-geyserutils.txt", latestTag);
        return available;
    }

    private Path resolveGeyserDir(Path pluginsDir) throws IOException {
        Path a = pluginsDir.resolve("Geyser");
        Path b = pluginsDir.resolve("geyser");
        if (Files.isDirectory(a) || Files.exists(a)) return a;
        if (Files.isDirectory(b) || Files.exists(b)) return b;
        Files.createDirectories(a);
        return a;
    }

    private void deleteMatching(Path dir, String pattern) {
        try {
            if (!Files.isDirectory(dir)) return;
            PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + pattern);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    if (matcher.matches(p.getFileName())) {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private String normalizeTag(String tag) {
        if (tag == null) return "";
        return tag.trim().replaceFirst("^v", "");
    }

    private String readInstalledTag(Path dataDir, String fileName) {
        Path marker = dataDir.resolve(fileName);
        try {
            if (!Files.exists(marker)) return "";
            return Files.readString(marker, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void writeInstalledTag(Path dataDir, String fileName, String tag) throws IOException {
        Files.createDirectories(dataDir);
        Path marker = dataDir.resolve(fileName);
        Files.writeString(marker, tag == null ? "" : tag.trim(), StandardCharsets.UTF_8);
    }
}

