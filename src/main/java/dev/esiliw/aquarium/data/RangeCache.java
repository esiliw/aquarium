/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Byte ranges of large remote files (cloud-optimised GeoTIFFs), each range cached on disk once downloaded.
 * Like {@link TileCache}, failures are retried rather than treated as missing data; a 404/403 means the file doesn't exist.
 */
public final class RangeCache {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Path dir;

    public RangeCache(String name) {
        this.dir = DataEnv.cacheRoot().resolve(name);
    }

    /** bytes [offset, offset+length) of the file, or null if the file doesn't exist on the server */
    public byte[] get(String fileName, String url, long offset, int length) {
        Path file = this.dir.resolve(fileName).resolve(offset + "-" + length);
        Path none = this.dir.resolve(fileName + ".none");
        try {
            if (Files.isRegularFile(none)) return null;
            if (Files.isRegularFile(file)) return Files.readAllBytes(file);
        } catch (IOException ignored) {
        }
        byte[] bytes = download(url, offset, length);
        try {
            if (bytes == null) {
                Files.createDirectories(none.getParent());
                Files.write(none, new byte[0]);
                return null;
            }
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(offset + "-" + length + ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DataEnv.LOGGER.warn("Failed to cache range of {}", url, e);
        }
        return bytes;
    }

    private static byte[] download(String url, long offset, int length) {
        long pause = 1000;
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90))
                        .header("User-Agent", "Aquarium/" + DataEnv.version() + " (Minecraft mod; github.com/esiliw/aquarium)")
                        .header("Range", "bytes=" + offset + "-" + (offset + length - 1)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                int code = r.statusCode();
                if (code == 206) return r.body();
                if (code == 200) {                                      // server ignored the range: cut it out ourselves
                    byte[] all = r.body();
                    int from = (int) Math.min(all.length, offset), to = (int) Math.min(all.length, offset + length);
                    return java.util.Arrays.copyOfRange(all, from, to);
                }
                if (code == 416) return new byte[0];
                if (code == 404 || code == 403) return null;
                DataEnv.LOGGER.warn("HTTP {} for {} (attempt {})", code, url, attempt);
            } catch (IOException e) {
                DataEnv.LOGGER.warn("Download failed for {} (attempt {}): {}", url, attempt, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while downloading " + url);
            }
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while downloading " + url);
            }
            pause = Math.min(30000, pause * 2);
        }
    }
}
