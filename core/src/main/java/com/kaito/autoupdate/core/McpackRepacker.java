package com.kaito.autoupdate.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class McpackRepacker {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public void repack(Path downloadedMcpack, Path finalMcpack, String headerName) throws IOException {
        Path tempDir = Files.createTempDirectory("geyser_display_entity_pack-");
        try {
            unzip(downloadedMcpack, tempDir);

            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().toLowerCase().endsWith(".png")) {
                        Files.deleteIfExists(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            Path manifest = tempDir.resolve("manifest.json");
            if (Files.exists(manifest)) {
                String json = Files.readString(manifest, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                if (root.has("header") && root.get("header").isJsonObject()) {
                    JsonObject header = root.getAsJsonObject("header");
                    if (headerName != null && !headerName.isEmpty()) {
                        header.addProperty("name", headerName);
                    }
                    header.addProperty("uuid", UUID.randomUUID().toString());
                }

                if (root.has("modules") && root.get("modules").isJsonArray() && root.getAsJsonArray("modules").size() > 0) {
                    JsonElement first = root.getAsJsonArray("modules").get(0);
                    if (first != null && first.isJsonObject()) {
                        first.getAsJsonObject().addProperty("uuid", UUID.randomUUID().toString());
                    }
                }

                Files.writeString(manifest, GSON.toJson(root), StandardCharsets.UTF_8);
            }

            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().toLowerCase().endsWith(".json") && !file.equals(tempDir.resolve("manifest.json"))) {
                        try {
                            String json = Files.readString(file, StandardCharsets.UTF_8);
                            JsonElement el = JsonParser.parseString(json);
                            Files.writeString(file, GSON.toJson(el), StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            zipDirectory(tempDir, finalMcpack);
        } finally {
            deleteDirectoryRecursive(tempDir);
        }
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (InputStream raw = Files.newInputStream(zipFile); ZipInputStream zis = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    zis.closeEntry();
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    zis.transferTo(os);
                }
                zis.closeEntry();
            }
        }
    }

    private void zipDirectory(Path sourceDir, Path zipOut) throws IOException {
        Files.createDirectories(zipOut.getParent());
        Path temp = zipOut.resolveSibling(zipOut.getFileName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(temp);
             ZipOutputStream zos = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = sourceDir.relativize(file);
                    ZipEntry entry = new ZipEntry(rel.toString().replace('\\', '/'));
                    zos.putNextEntry(entry);
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zos);
                    }
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.move(temp, zipOut, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void deleteDirectoryRecursive(Path dir) {
        if (dir == null) return;
        try {
            if (!Files.exists(dir)) return;
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }
}

