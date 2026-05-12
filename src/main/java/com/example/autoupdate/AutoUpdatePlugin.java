package com.example.autoupdate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoUpdatePlugin extends JavaPlugin {
    private static final String LAST_RELEASE_FILE = "last-installed-release.txt";
    private GithubReleaseChecker releaseChecker;
    private HttpClient httpClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.releaseChecker = new GithubReleaseChecker(this);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        if (getConfig().getBoolean("auto-update-on-startup", true)) {
            checkAndUpdate(Bukkit.getConsoleSender(), true, true);
        } else if (getConfig().getBoolean("notify-on-startup", true)) {
            checkAndUpdate(Bukkit.getConsoleSender(), true, false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("autoupdate")) {
            return false;
        }

        boolean doUpdate = args.length == 0 || args[0].equalsIgnoreCase("update");
        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            doUpdate = false;
        }

        checkAndUpdate(sender, false, doUpdate);
        return true;
    }

    private void checkAndUpdate(CommandSender receiver, boolean startupNotify, boolean allowAutoUpdate) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String installedTag = readInstalledReleaseTag();
                GithubReleaseChecker.UpdateResult result = releaseChecker.checkForUpdate(installedTag);

                if (result.hasUpdate()) {
                    receiver.sendMessage("§e[AutoUpdate] Co ban moi: §a" + result.latestVersion());
                    receiver.sendMessage("§e[AutoUpdate] Da cai: §c" + (installedTag.isBlank() ? "chua co" : installedTag));
                    receiver.sendMessage("§e[AutoUpdate] Tai tai: §b" + result.releaseUrl());
                    if (allowAutoUpdate) {
                        performAssetUpdate(receiver, result);
                    }
                } else if (!startupNotify) {
                    receiver.sendMessage("§a[AutoUpdate] Ban dang dung phien ban moi nhat (" + result.latestVersion() + ").");
                }
            } catch (IOException e) {
                if (!startupNotify) {
                    receiver.sendMessage("§c[AutoUpdate] Khong the kiem tra update: " + e.getMessage());
                }
                getLogger().warning("Khong the kiem tra update: " + e.getMessage());
            }
        });
    }

    private void performAssetUpdate(CommandSender receiver, GithubReleaseChecker.UpdateResult result) throws IOException {
        String jarExt = getConfig().getString("required-jar-suffix", ".jar");
        String mcpackExt = getConfig().getString("required-mcpack-suffix", ".mcpack");

        Optional<GithubReleaseChecker.ReleaseAsset> jarAsset =
            result.assets().stream().filter(a -> a.name().endsWith(jarExt)).findFirst();
        Optional<GithubReleaseChecker.ReleaseAsset> mcpackAsset =
            result.assets().stream().filter(a -> a.name().endsWith(mcpackExt)).findFirst();

        if (jarAsset.isEmpty() || mcpackAsset.isEmpty()) {
            throw new IOException("Release thieu asset .jar hoac .mcpack");
        }

        Path pluginsDir = getDataFolder().toPath().getParent();
        if (pluginsDir == null) {
            throw new IOException("Khong tim thay thu muc plugins");
        }

        Path jarTarget = pluginsDir.resolve(getConfig().getString("target-jar-path", "Geyser/extensions/DisplayEntity.jar"));
        Path mcpackTarget = pluginsDir.resolve(getConfig().getString("target-mcpack-path", "Geyser/packs/DisplayEntityPack.mcpack"));

        downloadTo(jarAsset.get().downloadUrl(), jarTarget);
        downloadTo(mcpackAsset.get().downloadUrl(), mcpackTarget);
        writeInstalledReleaseTag(result.latestVersion());

        receiver.sendMessage("§a[AutoUpdate] Da cap nhat xong file:");
        receiver.sendMessage("§a- " + jarTarget);
        receiver.sendMessage("§a- " + mcpackTarget);
        receiver.sendMessage("§e[AutoUpdate] Nen restart server de tai lai plugin/pack.");
    }

    private void downloadTo(String url, Path target) throws IOException {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new IOException("Khong tao duoc thu muc: " + target.getParent(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "AutoUpdatePlugin")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tai file bi gian doan", e);
        }

        if (response.statusCode() >= 400) {
            throw new IOException("Khong tai duoc asset, HTTP " + response.statusCode());
        }

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private String readInstalledReleaseTag() {
        Path marker = getDataFolder().toPath().resolve(LAST_RELEASE_FILE);
        try {
            if (!Files.exists(marker)) {
                return "";
            }
            return Files.readString(marker, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            getLogger().warning("Khong doc duoc marker release: " + e.getMessage());
            return "";
        }
    }

    private void writeInstalledReleaseTag(String tag) throws IOException {
        Path marker = getDataFolder().toPath().resolve(LAST_RELEASE_FILE);
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, tag == null ? "" : tag.trim(), StandardCharsets.UTF_8);
    }
}
