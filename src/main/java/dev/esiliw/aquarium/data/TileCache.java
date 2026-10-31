/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0.
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads tiles addressed by (z, x, y), keeps the raw bytes on disk forever and the decoded tiles in a memory LRU.
 * A failed download is retried with growing pauses instead of being treated as "no data", so a server hiccup can never
 * bake holes or seams into the world. Only a definite 404 counts as missing.
 */
public final class TileCache<T> {
    public interface Url {
        String get(int z, int x, int y);
    }

    public interface Decoder<T> {
        T decode(byte[] bytes) throws IOException;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private final String name;
    private final Url url;
    private final Decoder<T> decoder;
    private final Path dir;
    private final Map<Long, Object> loaded;
    private final ConcurrentHashMap<Long, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    public TileCache(String name, Url url, Decoder<T> decoder, int memoryTiles) {
        this.name = name;
        this.url = url;
        this.decoder = decoder;
        this.dir = DataEnv.cacheRoot().resolve(name);
        this.loaded = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Object> e) {
                return this.size() > memoryTiles;
            }
        };
    }

    private static long key(int z, int x, int y) {
        return ((long) z << 58) | ((long) (x & 0x1FFFFFFF) << 29) | (y & 0x1FFFFFFF);
    }

    /** the decoded tile, or null if the server says it doesn't exist. Blocks until it is available. */
    @SuppressWarnings("unchecked")
    public T get(int z, int x, int y) {
        long k = key(z, x, y);
        synchronized (this.loaded) {
            Object o = this.loaded.get(k);
            if (o != null) return o == MISSING ? null : (T) o;
        }
        CompletableFuture<Object> f = this.pending.computeIfAbsent(k, kk -> CompletableFuture.supplyAsync(() -> this.load(z, x, y), DataEnv.IO_EXECUTOR));
        Object o;
        try {
            o = f.join();
        } finally {
            this.pending.remove(k, f);
        }
        synchronized (this.loaded) {
            this.loaded.put(k, o);
        }
        return o == MISSING ? null : (T) o;
    }

    private Object load(int z, int x, int y) {
        Path file = this.dir.resolve(z + "/" + x + "/" + y);
        Path none = this.dir.resolve(z + "/" + x + "/" + y + ".none");
        try {
            if (Files.isRegularFile(none)) return MISSING;
            if (Files.isRegularFile(file)) return this.decoder.decode(Files.readAllBytes(file));
        } catch (IOException e) {
            DataEnv.LOGGER.warn("Bad cached {} tile {}, downloading again ({})", this.name, file, e.toString());
        }
        byte[] bytes = this.download(z, x, y);
        try {
            Files.createDirectories(file.getParent());
            if (bytes == null) {
                Files.write(none, new byte[0]);
                return MISSING;
            }
            Path tmp = file.resolveSibling(y + ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DataEnv.LOGGER.warn("Failed to cache {} tile {}", this.name, file, e);
        }
        if (bytes == null) return MISSING;
        try {
            return this.decoder.decode(bytes);
        } catch (IOException e) {
            DataEnv.LOGGER.error("Undecodable {} tile {}/{}/{}: {}", this.name, z, x, y, e.toString());
            return MISSING;
        }
    }

    /** bytes, or null for a definite 404. Keeps retrying anything else (with pauses up to 30 s). */
    private byte[] download(int z, int x, int y) {
        String u = this.url.get(z, x, y);
        long pause = 1000;
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Aquarium/" + DataEnv.version() + " (Minecraft mod; github.com/esiliw/aquarium)")
                        .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200) return r.body();
                if (r.statusCode() == 404 || r.statusCode() == 403 && u.contains("amazonaws.com")) return null;   // S3 answers 403 for absent keys
                DataEnv.LOGGER.warn("HTTP {} for {} (attempt {})", r.statusCode(), u, attempt);
            } catch (IOException e) {
                DataEnv.LOGGER.warn("Download failed for {} (attempt {}): {}", u, attempt, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while downloading " + u);
            }
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while downloading " + u);
            }
            pause = Math.min(30000, pause * 2);
        }
    }
}
