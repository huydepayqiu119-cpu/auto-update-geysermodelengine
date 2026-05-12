package com.kaito.autoupdate.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GithubReleaseClient {
    private final HttpClient client;
    private final String userAgent;

    public GithubReleaseClient(HttpClient client, String userAgent) {
        this.client = client;
        this.userAgent = userAgent;
    }

    public ReleaseInfo fetchFirstRelease(String owner, String repo) throws IOException {
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases?per_page=1";
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("request interrupted", e);
        }

        if (response.statusCode() >= 400) {
            throw new IOException("GitHub HTTP " + response.statusCode());
        }

        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) {
            throw new IOException("unexpected GitHub response");
        }

        JsonArray arr = root.getAsJsonArray();
        if (arr.isEmpty()) {
            throw new IOException("no releases");
        }

        JsonObject rel = arr.get(0).getAsJsonObject();
        String tag = getString(rel, "tag_name");
        String htmlUrl = getString(rel, "html_url");

        List<ReleaseInfo.ReleaseAsset> assets = new ArrayList<>();
        if (rel.has("assets") && rel.get("assets").isJsonArray()) {
            for (JsonElement el : rel.getAsJsonArray("assets")) {
                if (!el.isJsonObject()) continue;
                JsonObject a = el.getAsJsonObject();
                String name = getString(a, "name");
                String dl = getString(a, "browser_download_url");
                assets.add(new ReleaseInfo.ReleaseAsset(name, dl));
            }
        }

        return new ReleaseInfo(tag, htmlUrl, assets);
    }

    private String getString(JsonObject obj, String key) throws IOException {
        if (!obj.has(key)) throw new IOException("missing " + key);
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive()) throw new IOException("invalid " + key);
        return el.getAsString();
    }
}
