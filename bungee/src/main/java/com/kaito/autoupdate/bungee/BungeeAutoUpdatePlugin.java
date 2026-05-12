package com.kaito.autoupdate.bungee;

import com.kaito.autoupdate.core.UpdateConfig;
import com.kaito.autoupdate.core.UpdateSummary;
import com.kaito.autoupdate.core.Updater;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class BungeeAutoUpdatePlugin extends Plugin {
    private Updater updater;
    private HttpClient httpClient;
    private Configuration cfg;

    @Override
    public void onEnable() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            java.io.File file = new java.io.File(getDataFolder(), "config.yml");
            if (!file.exists()) {
                try (java.io.InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) java.nio.file.Files.copy(in, file.toPath());
                }
            }
            this.cfg = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (Exception e) {
            getLogger().warning("config load failed: " + e.getMessage());
        }

        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.updater = new Updater(httpClient, "AutoUpdatePlugin");
        getLogger().info("Author: " + Updater.AUTHOR);

        runCheck(ProxyServer.getInstance().getConsole(), true, true);

        int intervalMinutes = getInt("auto-update-interval-minutes", 0);
        if (intervalMinutes <= 0) intervalMinutes = UpdateConfig.defaults().autoUpdateIntervalMinutes();
        long intervalMs = intervalMinutes * 60L * 1000L;
        ProxyServer.getInstance().getScheduler().schedule(this, () -> {
            runCheck(ProxyServer.getInstance().getConsole(), true, true);
        }, intervalMs, intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void runCheck(CommandSender receiver, boolean startup, boolean doUpdate) {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            try {
                UpdateConfig c = readConfig();
                Path pluginsDir = getDataFolder().toPath().getParent();
                if (pluginsDir == null) throw new IOException("missing plugins dir");
                UpdateSummary s = updater.checkAndMaybeUpdateAll(c, pluginsDir, getDataFolder().toPath(), doUpdate);
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
                    }
                } else if (!startup) {
                    receiver.sendMessage("§a[AutoUpdate] Khong co update moi.");
                    receiver.sendMessage("§7[AutoUpdate] Author: " + Updater.AUTHOR);
                }
            } catch (IOException e) {
                if (!startup) receiver.sendMessage("§c[AutoUpdate] Khong the kiem tra update: " + e.getMessage());
                getLogger().warning("Khong the kiem tra update: " + e.getMessage());
            }
        });
    }

    private UpdateConfig readConfig() {
        UpdateConfig d = UpdateConfig.defaults();
        return new UpdateConfig(
            getString("github-owner", d.githubOwner()),
            getString("github-repo", d.githubRepo()),
            getBoolean("notify-on-startup", d.notifyOnStartup()),
            getBoolean("auto-update-on-startup", d.autoUpdateOnStartup()),
            getInt("auto-update-interval-minutes", d.autoUpdateIntervalMinutes()),
            getString("target-jar-path", d.targetJarPath()),
            getString("target-mcpack-path", d.targetMcpackPath()),
            getString("required-jar-suffix", d.requiredJarSuffix()),
            getString("required-mcpack-suffix", d.requiredMcpackSuffix()),
            getString("mcpack-header-name", d.mcpackHeaderName())
        );
    }

    private String getString(String key, String def) {
        try {
            if (cfg == null) return def;
            String v = cfg.getString(key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    private boolean getBoolean(String key, boolean def) {
        try {
            if (cfg == null) return def;
            return cfg.getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private int getInt(String key, int def) {
        try {
            if (cfg == null) return def;
            return cfg.getInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }
}

