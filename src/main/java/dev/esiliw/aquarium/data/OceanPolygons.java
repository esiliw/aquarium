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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
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

    /** locator for inside/outside, coast = only the real coastline segments (not the cell's clipping box), in scaled coords */
    private record Cell(IndexedPointInAreaLocator locator, IndexedFacetDistance coast, double xScale, boolean empty) {
    }

    private static final Cell EMPTY = new Cell(null, null, 1, true);
    private static final double METERS_PER_DEGREE = GeoProjection.EQUATOR_CIRCUMFERENCE / 360.0;

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

    /**
     * Distance in metres from a point to the nearest real coastline, capped at maxMeters
     * (also looks into neighbouring cells when the cell edge is closer than the coast found so far).
     */
    public double coastDistance(double lat, double lon, double maxMeters) {
        int cx = (int) Math.floor(lon), cy = (int) Math.floor(lat);
        double best = maxMeters;
        double maxDeg = maxMeters / METERS_PER_DEGREE;
        double cos = Math.max(0.05, Math.cos(Math.toRadians(lat)));
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx, ny = cy + dy;
                if (nx < -180 || nx >= 180 || ny < -90 || ny >= 90) continue;
                if (dx != 0 || dy != 0) {
                    // skip neighbours further away than the best distance so far
                    double ex = dx < 0 ? (lon - cx) * cos : dx > 0 ? (cx + 1 - lon) * cos : 0;
                    double ey = dy < 0 ? lat - cy : dy > 0 ? cy + 1 - lat : 0;
                    if (Math.hypot(ex, ey) * METERS_PER_DEGREE >= best) continue;
                }
                Cell cell = this.cell(nx, ny);
                if (cell.empty || cell.coast == null) continue;
                double d = cell.coast.distance(GF.createPoint(new Coordinate(lon * cell.xScale, lat)));
                best = Math.min(best, d * METERS_PER_DEGREE);
            }
        }
        return Math.min(best, maxMeters);
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
            double xScale = Math.max(0.05, Math.cos(Math.toRadians(cy + 0.5)));
            return new Cell(new IndexedPointInAreaLocator(all), coastLines(parts, cx, cy, xScale), xScale, false);
        } catch (Exception e) {
            DataEnv.LOGGER.warn("Bad ocean cell {}/{}: {}", cx, cy, e.toString());
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
            return EMPTY;
        }
    }

    /** the polygon boundaries minus the parts that only run along the 1x1 degree clipping box */
    private static IndexedFacetDistance coastLines(List<Polygon> parts, int cx, int cy, double xScale) {
        // the clipping box is wherever the polygons reach their outermost extent (it may have a small margin around the cell)
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Polygon poly : parts) {
            for (Coordinate c : poly.getExteriorRing().getCoordinates()) {
                box[0] = Math.min(box[0], c.x);
                box[1] = Math.min(box[1], c.y);
                box[2] = Math.max(box[2], c.x);
                box[3] = Math.max(box[3], c.y);
            }
        }
        List<LineString> lines = new ArrayList<>();
        for (Polygon poly : parts) {
            addCoast(poly.getExteriorRing().getCoordinates(), box, xScale, lines);
            for (int h = 0; h < poly.getNumInteriorRing(); h++) addCoast(poly.getInteriorRingN(h).getCoordinates(), box, xScale, lines);
        }
        if (lines.isEmpty()) return null;
        return new IndexedFacetDistance(GF.createMultiLineString(lines.toArray(new LineString[0])));
    }

    private static void addCoast(Coordinate[] ring, double[] box, double xScale, List<LineString> out) {
        List<Coordinate> run = new ArrayList<>();
        for (int i = 0; i + 1 < ring.length; i++) {
            Coordinate a = ring[i], b = ring[i + 1];
            if (onBox(a, b, box)) {
                flush(run, out);
                continue;
            }
            if (run.isEmpty()) run.add(new Coordinate(a.x * xScale, a.y));
            run.add(new Coordinate(b.x * xScale, b.y));
        }
        flush(run, out);
    }

    private static void flush(List<Coordinate> run, List<LineString> out) {
        if (run.size() >= 2) out.add(GF.createLineString(run.toArray(new Coordinate[0])));
        run.clear();
    }

    private static boolean onBox(Coordinate a, Coordinate b, double[] box) {
        final double eps = 1e-6;
        return (Math.abs(a.x - box[0]) < eps && Math.abs(b.x - box[0]) < eps) || (Math.abs(a.x - box[2]) < eps && Math.abs(b.x - box[2]) < eps)
                || (Math.abs(a.y - box[1]) < eps && Math.abs(b.y - box[1]) < eps) || (Math.abs(a.y - box[3]) < eps && Math.abs(b.y - box[3]) < eps);
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
