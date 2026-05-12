package com.kaito.autoupdate.paper;

import com.kaito.autoupdate.core.UpdateConfig;
import com.kaito.autoupdate.core.UpdateSummary;
import com.kaito.autoupdate.core.Updater;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperAutoUpdatePlugin extends JavaPlugin {
    private Updater updater;
    private HttpClient httpClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.updater = new Updater(httpClient, "AutoUpdatePlugin");
        getLogger().info("Author: " + Updater.AUTHOR);

        runCheck(Bukkit.getConsoleSender(), true, true);

        int intervalMinutes = getConfig().getInt("auto-update-interval-minutes", 0);
        if (intervalMinutes <= 0) intervalMinutes = UpdateConfig.defaults().autoUpdateIntervalMinutes();
        long intervalTicks = intervalMinutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            runCheck(Bukkit.getConsoleSender(), true, true);
        }, intervalTicks, intervalTicks);
    }

    private void runCheck(org.bukkit.command.CommandSender receiver, boolean startup, boolean doUpdate) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                UpdateConfig cfg = readConfig();
                Path pluginsDir = getDataFolder().toPath().getParent();
                if (pluginsDir == null) throw new IOException("missing plugins dir");
                UpdateSummary s = updater.checkAndMaybeUpdateAll(cfg, pluginsDir, getDataFolder().toPath(), doUpdate);
                if (s.hasAnyUpdate()) {
                    for (String line : s.updatesAvailable()) {
                        receiver.sendMessage("§e[AutoUpdate] " + line);
                    }
                    receiver.sendMessage("§7[AutoUpdate] Author: " + Updater.AUTHOR);
                    if (doUpdate) {
                        receiver.sendMessage("§a[AutoUpdate] Da cap nhat xong file:");
                        for (String file : s.updatedFiles()) {
                            receiver.sendMessage("§a- " + file);
                        }
                        receiver.sendMessage("§e[AutoUpdate] Nen restart server de tai lai plugin/pack.");
                    }
                } else if (!startup) {
                    receiver.sendMessage("§a[AutoUpdate] Khong co update moi.");
                    receiver.sendMessage("§7[AutoUpdate] Author: " + Updater.AUTHOR);
                }
            } catch (IOException e) {
                if (!startup) {
                    receiver.sendMessage("§c[AutoUpdate] Khong the kiem tra update: " + e.getMessage());
                }
                getLogger().warning("Khong the kiem tra update: " + e.getMessage());
            }
        });
    }

    private UpdateConfig readConfig() {
        return new UpdateConfig(
            getConfig().getString("github-owner", UpdateConfig.defaults().githubOwner()),
            getConfig().getString("github-repo", UpdateConfig.defaults().githubRepo()),
            getConfig().getBoolean("notify-on-startup", UpdateConfig.defaults().notifyOnStartup()),
            getConfig().getBoolean("auto-update-on-startup", UpdateConfig.defaults().autoUpdateOnStartup()),
            getConfig().getInt("auto-update-interval-minutes", UpdateConfig.defaults().autoUpdateIntervalMinutes()),
            getConfig().getString("target-jar-path", UpdateConfig.defaults().targetJarPath()),
            getConfig().getString("target-mcpack-path", UpdateConfig.defaults().targetMcpackPath()),
            getConfig().getString("required-jar-suffix", UpdateConfig.defaults().requiredJarSuffix()),
            getConfig().getString("required-mcpack-suffix", UpdateConfig.defaults().requiredMcpackSuffix()),
            getConfig().getString("mcpack-header-name", UpdateConfig.defaults().mcpackHeaderName())
        );
    }
}

