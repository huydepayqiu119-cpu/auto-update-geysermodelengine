package com.kaito.autoupdate.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FileDownloader {
    private final HttpClient client;
    private final String userAgent;

    public FileDownloader(HttpClient client, String userAgent) {
        this.client = client;
        this.userAgent = userAgent;
    }

    public void downloadTo(String url, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", userAgent)
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download interrupted", e);
        }

        if (response.statusCode() >= 400) {
            throw new IOException("download HTTP " + response.statusCode());
        }

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}

