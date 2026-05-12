package com.example.autoupdate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.plugin.java.JavaPlugin;

public class GithubReleaseChecker {
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSET_PATTERN = Pattern.compile(
        "\\{[^\\{\\}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\\}"
    );
    private final JavaPlugin plugin;
    private final HttpClient client;

    public GithubReleaseChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public UpdateResult checkForUpdate(String currentVersion) throws IOException {
        String owner = plugin.getConfig().getString("github-owner", "").trim();
        String repo = plugin.getConfig().getString("github-repo", "").trim();

        if (owner.isEmpty() || repo.isEmpty()) {
            throw new IOException("Thieu github-owner/github-repo trong config.yml");
        }

        // Use the first release in list instead of /latest, because maintainer may not mark latest correctly.
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=1";
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AutoUpdatePlugin")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Yeu cau bi gian doan", e);
        }

        if (response.statusCode() >= 400) {
            throw new IOException("GitHub API tra ve loi HTTP " + response.statusCode());
        }

        String body = response.body();
        String latestTag = extractValue(TAG_PATTERN, body);
        String releaseUrl = extractValue(URL_PATTERN, body);
        List<ReleaseAsset> assets = extractAssets(body);

        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedLatest = normalizeVersion(latestTag);

        boolean hasUpdate = !normalizedLatest.equals(normalizedCurrent);
        return new UpdateResult(hasUpdate, currentVersion, latestTag, releaseUrl, assets);
    }

    private String extractValue(Pattern pattern, String source) throws IOException {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            throw new IOException("Khong tim thay du lieu can thiet trong phan hoi GitHub");
        }
        return matcher.group(1);
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().replaceFirst("^v", "");
    }

    private List<ReleaseAsset> extractAssets(String source) {
        List<ReleaseAsset> assets = new ArrayList<>();
        Matcher matcher = ASSET_PATTERN.matcher(source);
        while (matcher.find()) {
            assets.add(new ReleaseAsset(matcher.group(1), matcher.group(2)));
        }
        return assets;
    }

    public record UpdateResult(
        boolean hasUpdate,
        String currentVersion,
        String latestVersion,
        String releaseUrl,
        List<ReleaseAsset> assets
    ) {}

    public record ReleaseAsset(String name, String downloadUrl) {}
}
