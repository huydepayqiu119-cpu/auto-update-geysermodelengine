package com.kaito.autoupdate.velocity;

import com.google.gson.Gson;
import com.kaito.autoupdate.core.UpdateConfig;
import com.kaito.autoupdate.core.UpdateSummary;
import com.kaito.autoupdate.core.Updater;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@Plugin(id = "autoupdateplugin", name = "AutoUpdatePlugin", version = "1.0.0", authors = {"Kaito"})
public class VelocityAutoUpdatePlugin {
    private static final Gson GSON = new Gson();
    private final ProxyServer server;
    private final java.util.logging.Logger logger;
    private Updater updater;
    private UpdateConfig cfg;
    private HttpClient httpClient;
    private final Path dataDir;

    @Inject
    public VelocityAutoUpdatePlugin(ProxyServer server, java.util.logging.Logger logger, @DataDirectory Path dataDir) {
        this.server = server;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.updater = new Updater(httpClient, "AutoUpdatePlugin");
        loadConfig();
        logger.info("Author: " + Updater.AUTHOR);

        runCheck(server.getConsoleCommandSource(), true, true);

        int interval = cfg.autoUpdateIntervalMinutes() > 0 ? cfg.autoUpdateIntervalMinutes() : UpdateConfig.defaults().autoUpdateIntervalMinutes();
        server.getScheduler().buildTask(this, () -> {
            runCheck(server.getConsoleCommandSource(), true, true);
        }).repeat(interval, TimeUnit.MINUTES).schedule();
    }

    private void runCheck(com.velocitypowered.api.command.CommandSource receiver, boolean startup, boolean doUpdate) {
        server.getScheduler().buildTask(this, () -> {
            try {
                Path pluginsDir = Path.of("plugins");
                UpdateSummary s = updater.checkAndMaybeUpdateAll(cfg, pluginsDir, dataDir, doUpdate);
                if (s.hasAnyUpdate()) {
                    for (String line : s.updatesAvailable()) {
                        receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] " + line));
                    }
                    receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] Author: " + Updater.AUTHOR));
                    if (doUpdate) {
                        receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] Da cap nhat xong file:"));
                        for (String file : s.updatedFiles()) {
                            receiver.sendMessage(net.kyori.adventure.text.Component.text("- " + file));
                        }
                    }
                } else if (!startup) {
                    receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] Khong co update moi."));
                    receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] Author: " + Updater.AUTHOR));
                }
            } catch (IOException e) {
                if (!startup) {
                    receiver.sendMessage(net.kyori.adventure.text.Component.text("[AutoUpdate] Khong the kiem tra update: " + e.getMessage()));
                }
                logger.warning("Khong the kiem tra update: " + e.getMessage());
            }
        }).schedule();
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve("config.json");
            if (!Files.exists(file)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
                    if (in != null) Files.copy(in, file);
                }
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            VelocityConfig vc = GSON.fromJson(json, VelocityConfig.class);
            this.cfg = vc == null ? UpdateConfig.defaults() : vc.toUpdateConfig();
        } catch (Exception e) {
            this.cfg = UpdateConfig.defaults();
        }
    }

    private static class VelocityConfig {
        String githubOwner;
        String githubRepo;
        Boolean notifyOnStartup;
        Boolean autoUpdateOnStartup;
        Integer autoUpdateIntervalMinutes;
        String targetJarPath;
        String targetMcpackPath;
        String requiredJarSuffix;
        String requiredMcpackSuffix;
        String mcpackHeaderName;

        UpdateConfig toUpdateConfig() {
            UpdateConfig d = UpdateConfig.defaults();
            return new UpdateConfig(
                githubOwner == null ? d.githubOwner() : githubOwner,
                githubRepo == null ? d.githubRepo() : githubRepo,
                notifyOnStartup == null ? d.notifyOnStartup() : notifyOnStartup,
                autoUpdateOnStartup == null ? d.autoUpdateOnStartup() : autoUpdateOnStartup,
                autoUpdateIntervalMinutes == null ? d.autoUpdateIntervalMinutes() : autoUpdateIntervalMinutes,
                targetJarPath == null ? d.targetJarPath() : targetJarPath,
                targetMcpackPath == null ? d.targetMcpackPath() : targetMcpackPath,
                requiredJarSuffix == null ? d.requiredJarSuffix() : requiredJarSuffix,
                requiredMcpackSuffix == null ? d.requiredMcpackSuffix() : requiredMcpackSuffix,
                mcpackHeaderName == null ? d.mcpackHeaderName() : mcpackHeaderName
            );
        }
    }
}

