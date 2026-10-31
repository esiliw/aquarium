/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0. Port of net.gegy1000.earth.server.world.data.source.OceanPolygonSource.
 */
package dev.esiliw.aquarium.data;

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.tukaani.xz.SingleXZInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precise ocean outlines (OpenStreetMap-derived) from the Terrarium geo3 server, one file per 1x1 degree cell.
 * {@link #oceanAt} returns TRUE / FALSE where a cell has outlines, or null where it has none (caller falls back to elevation).
 */
public final class OceanPolygons {
    private static final GeometryFactory GF = new GeometryFactory();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Path CACHE = DataEnv.cacheRoot().resolve("ocean");

    private record Cell(IndexedPointInAreaLocator locator, boolean empty) {
    }

    private static final Cell EMPTY = new Cell(null, true);

    private final Map<Long, Cell> cells = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cell> e) {
            return this.size() > 64;
        }
    };
    private final ConcurrentHashMap<Long, CompletableFuture<Cell>> pending = new ConcurrentHashMap<>();

    public Boolean oceanAt(double lat, double lon) {
        int cx = (int) Math.floor(lon), cy = (int) Math.floor(lat);
        if (cx < -180 || cx >= 180 || cy < -90 || cy >= 90) return null;
        Cell cell = this.cell(cx, cy);
        if (cell.empty) return null;
        return cell.locator.locate(new Coordinate(lon, lat)) != Location.EXTERIOR;
    }

    private Cell cell(int cx, int cy) {
        long key = ((long) cx << 32) | (cy & 0xFFFFFFFFL);
        synchronized (this.cells) {
            Cell c = this.cells.get(key);
            if (c != null) return c;
        }
        CompletableFuture<Cell> f = this.pending.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> this.load(cx, cy), DataEnv.IO_EXECUTOR));
        Cell c;
        try {
            c = f.join();
        } finally {
            this.pending.remove(key, f);
        }
        synchronized (this.cells) {
            this.cells.put(key, c);
        }
        return c;
    }

    private Cell load(int cx, int cy) {
        Path file = CACHE.resolve(cx + "/" + cy);
        byte[] bytes = null;
        try {
            if (Files.isRegularFile(file)) bytes = Files.readAllBytes(file);
        } catch (IOException ignored) {
        }
        if (bytes == null) {
            bytes = download(cx, cy);
            if (bytes == null) return EMPTY;
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, bytes);
            } catch (IOException e) {
                DataEnv.LOGGER.warn("Failed to cache ocean cell {}", file, e);
            }
        }
        if (bytes.length == 0) return EMPTY;
        try {
            DataInputStream in = new DataInputStream(new SingleXZInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes))));
            int count = in.readInt();
            if (count == 0) return EMPTY;
            List<Polygon> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                MultiPolygon mp = readMultiPolygon(in);
                for (int k = 0; k < mp.getNumGeometries(); k++) parts.add((Polygon) mp.getGeometryN(k));
            }
            if (parts.isEmpty()) return EMPTY;
            Geometry all = GF.createMultiPolygon(parts.toArray(new Polygon[0]));
            return new Cell(new IndexedPointInAreaLocator(all), false);
        } catch (Exception e) {
            DataEnv.LOGGER.warn("Bad ocean cell {}/{}: {}", cx, cy, e.toString());
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
            return EMPTY;
        }
    }

    private static byte[] download(int cx, int cy) {
        String url = GeoTileSource.ENDPOINT + "/ocean/" + cx + "/" + cy;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Aquarium/" + DataEnv.version()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() == 200) return r.body();
                if (r.statusCode() == 404) return new byte[0];
            } catch (IOException e) {
                DataEnv.LOGGER.warn("Ocean download failed {} ({})", url, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static MultiPolygon readMultiPolygon(DataInputStream in) throws IOException {
        int n = in.readInt();
        List<Polygon> polys = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LinearRing shell = readRing(in);
            int holes = in.readInt();
            List<LinearRing> inner = new ArrayList<>(holes);
            for (int h = 0; h < holes; h++) {
                LinearRing r = readRing(in);
                if (r != null) inner.add(r);
            }
            if (shell != null) polys.add(GF.createPolygon(shell, inner.toArray(new LinearRing[0])));
        }
        return GF.createMultiPolygon(polys.toArray(new Polygon[0]));
    }

    private static LinearRing readRing(DataInputStream in) throws IOException {
        int n = in.readInt();
        double minX = in.readDouble(), minY = in.readDouble(), maxX = in.readDouble(), maxY = in.readDouble();
        double w = maxX - minX, h = maxY - minY;
        Coordinate[] c = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            int px = in.readShort() & 0xFFFF, py = in.readShort() & 0xFFFF;
            c[i] = new Coordinate(minX + (double) px / 0xFFFF * w, minY + (double) py / 0xFFFF * h);
        }
        if (n < 4) return null;
        if (!c[0].equals2D(c[n - 1])) {                      // close the ring if the data didn't
            c = java.util.Arrays.copyOf(c, n + 1);
            c[n] = new Coordinate(c[0]);
        }
        try {
            return GF.createLinearRing(c);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
