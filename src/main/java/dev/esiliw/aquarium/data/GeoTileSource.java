/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Tile layout follows net.gegy1000.earth.server.world.data.source.StdSource.
 */
package dev.esiliw.aquarium.data;

import dev.esiliw.aquarium.Aquarium;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One layer of the Terrarium geo3 dataset at one zoom level.
 * Tiles are 1000x1000 px in an equirectangular grid of (2 * 3^zoom) x (3^zoom) tiles, row 0 at the north pole.
 * Downloaded tiles are cached on disk, decoded tiles in memory.
 */
public final class GeoTileSource {
    public static final String ENDPOINT = "https://terrarium.gegy.dev/geo3";
    public static final int TILE_SIZE = 1000;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String layer;
    private final int zoom;
    private final int format;
    private final int memoryTiles;
    private final Path cacheRoot;

    private final Map<Long, TerrariumRasterReader.Raster> loaded;
    private final ConcurrentHashMap<Long, CompletableFuture<TerrariumRasterReader.Raster>> pending = new ConcurrentHashMap<>();

    /** marker for tiles that don't exist (outside the dataset / server 404) */
    public static final TerrariumRasterReader.Raster MISSING = null;

    public GeoTileSource(String layer, int zoom, int format, int memoryTiles) {
        this.layer = layer;
        this.zoom = zoom;
        this.format = format;
        this.memoryTiles = memoryTiles;
        this.cacheRoot = FabricLoader.getInstance().getGameDir().resolve("aquarium-cache").resolve(layer).resolve(String.valueOf(zoom));
        this.loaded = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, TerrariumRasterReader.Raster> eldest) {
                return this.size() > GeoTileSource.this.memoryTiles;
            }
        };
    }

    public int zoom() {
        return this.zoom;
    }

    public static double tileCountY(int zoom) {
        return Math.pow(3, zoom);
    }

    public static double tileCountX(int zoom) {
        return tileCountY(zoom) * 2.0;
    }

    public static double globalWidth(int zoom) {
        return tileCountX(zoom) * TILE_SIZE;
    }

    public static double globalHeight(int zoom) {
        return tileCountY(zoom) * TILE_SIZE;
    }

    /** Pick the zoom whose pixels are closest to (but not coarser than needed for) the given metres per block. */
    public static int zoomForScale(double metersPerBlock, int minZoom, int maxZoom) {
        double z = Math.log(GeoProjection.EQUATOR_CIRCUMFERENCE / (2 * TILE_SIZE * metersPerBlock)) / Math.log(3);
        return Math.max(minZoom, Math.min(maxZoom, (int) Math.ceil(z - 1e-6)));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Get a decoded tile, downloading and caching it if needed. Blocks the calling thread. Returns null if missing. */
    public TerrariumRasterReader.Raster getTile(int tx, int ty) {
        long key = key(tx, ty);
        synchronized (this.loaded) {
            TerrariumRasterReader.Raster r = this.loaded.get(key);
            if (r != null) return r;
        }
        CompletableFuture<TerrariumRasterReader.Raster> future = this.pending.computeIfAbsent(key, k ->
                CompletableFuture.supplyAsync(() -> this.load(tx, ty), Aquarium.IO_EXECUTOR));
        TerrariumRasterReader.Raster r;
        try {
            r = future.join();
        } finally {
            this.pending.remove(key, future);
        }
        if (r != null) {
            synchronized (this.loaded) {
                this.loaded.put(key, r);
            }
        }
        return r;
    }

    private TerrariumRasterReader.Raster load(int tx, int ty) {
        int countX = (int) Math.ceil(tileCountX(this.zoom));
        int countY = (int) Math.ceil(tileCountY(this.zoom));
        if (tx < 0 || ty < 0 || tx >= countX || ty >= countY) return null;

        Path file = this.cacheRoot.resolve(tx + "/" + ty);
        byte[] bytes = null;
        try {
            if (Files.isRegularFile(file)) bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            Aquarium.LOGGER.warn("Failed to read cached tile {}", file, e);
        }
        if (bytes == null) {
            bytes = this.download(tx, ty);
            if (bytes == null) return null;
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(ty + ".part");
                Files.write(tmp, bytes);
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Aquarium.LOGGER.warn("Failed to cache tile {}", file, e);
            }
        }
        try {
            return TerrariumRasterReader.read(new ByteArrayInputStream(bytes), this.format);
        } catch (IOException e) {
            Aquarium.LOGGER.error("Corrupt {} tile {}/{}/{}, deleting cache entry", this.layer, this.zoom, tx, ty, e);
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    private byte[] download(int tx, int ty) {
        String url = ENDPOINT + "/" + this.layer + "/" + this.zoom + "/" + tx + "/" + ty;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Aquarium/" + Aquarium.VERSION + " (Fabric port of Terrarium)")
                        .GET().build();
                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) return resp.body();
                if (resp.statusCode() == 404) return null;
                Aquarium.LOGGER.warn("HTTP {} for {}", resp.statusCode(), url);
            } catch (IOException e) {
                Aquarium.LOGGER.warn("Download failed for {} (attempt {}): {}", url, attempt + 1, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
