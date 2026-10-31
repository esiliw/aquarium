/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Sea, lakes and rivers from OpenStreetMap (ODbL), via OpenFreeMap's OpenMapTiles vector tiles at zoom 14,
 * pinned to one dated snapshot per world. Each tile is rasterised once into a water class grid plus a distance-to-shore grid.
 */
public final class OsmWater {
    public static final int MAX_Z = 14, RES = 512;
    public static final byte LAND = 0, OCEAN = 1, LAKE = 2, RIVER = 3, STREAM = 4;
    public static final String DEFAULT_SNAPSHOT = "20261025_080012_pt";

    public record Tile(byte[] cls, short[] dist, byte[] ways) {      // dist: metres to the nearest non-water pixel (capped)
    }

    private final double blockMetersAtEquator;
    private final String pinned;
    private volatile String snapshot;

    private final int Z;

    private final boolean ways;

    public OsmWater(String snapshot, double blockMetersAtEquator) {
        this(snapshot, blockMetersAtEquator, false);
    }

    /** @param ways also rasterise roads, railways and stations as a building guide */
    public OsmWater(String snapshot, double blockMetersAtEquator, boolean ways) {
        this.ways = ways;
        this.pinned = snapshot;
        this.blockMetersAtEquator = blockMetersAtEquator;
        // vector-tile zoom whose 512 px raster has about one pixel per block (detail beyond that is wasted downloads)
        double z = Math.log(GeoProjection.EQUATOR_CIRCUMFERENCE / (RES * blockMetersAtEquator)) / Math.log(2);
        this.Z = Math.max(6, Math.min(MAX_Z, (int) Math.ceil(z - 1e-6)));
    }

    /** the pinned snapshot if the server still has it, otherwise the newest one (resolved once) */
    private String snapshot() {
        String s = this.snapshot;
        if (s != null) return s;
        synchronized (this) {
            if (this.snapshot != null) return this.snapshot;
            String use = this.pinned;
            try {
                java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
                int code = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://tiles.openfreemap.org/planet/" + this.pinned + "/0/0/0.pbf"))
                        .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code == 404) {
                    String json = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://tiles.openfreemap.org/planet")).build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("/planet/([0-9_a-z]+)/\\{z\\}").matcher(json);
                    if (m.find()) {
                        use = m.group(1);
                        DataEnv.LOGGER.warn("OSM snapshot {} is no longer available, using {} for areas not cached yet", this.pinned, use);
                    }
                }
            } catch (Exception e) {
                DataEnv.LOGGER.warn("Could not check OSM snapshot {}: {}", this.pinned, e.toString());
                return this.pinned;              // try again next time
            }
            this.snapshot = use;
            return use;
        }
    }

    /** water class and distance to shore (m) at a point; known = false where OSM has no tile (decide from elevation) */
    public record Sample(byte cls, double dist, boolean known, byte way) {
    }

    public Sample sample(double lat, double lon) {
        int Z = this.Z;
        double fx = AwsElevation.pixelX(lon, Z) / AwsElevation.SIZE, fy = AwsElevation.pixelY(lat, Z) / AwsElevation.SIZE;
        int n = 1 << Z;
        int tx = Math.floorMod((int) Math.floor(fx), n), ty = Math.max(0, Math.min(n - 1, (int) Math.floor(fy)));
        Tile t = this.tile(tx, ty, lat);
        if (t == null) return new Sample(LAND, 0, false, OsmWays.NONE);
        int px = (int) Math.floor((fx - Math.floor(fx)) * RES), py = (int) Math.floor((fy - Math.floor(fy)) * RES);
        px = Math.max(0, Math.min(RES - 1, px));
        py = Math.max(0, Math.min(RES - 1, py));
        int i = px + py * RES;
        return new Sample(t.cls[i], t.dist[i], true, t.ways == null ? OsmWays.NONE : t.ways[i]);
    }

    private final java.util.LinkedHashMap<Long, Tile> lru = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Tile> e) {
            return this.size() > 48;
        }
    };

    private static final Tile MISSING = new Tile(new byte[0], new short[0]);

    private Tile tile(int tx, int ty, double lat) {
        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
        synchronized (this.lru) {
            Tile t = this.lru.get(key);
            if (t == MISSING) return null;
            if (t != null) return t;
        }
        byte[][] both = this.rasters(tx, ty);
        Tile t = both == null ? null : this.withDistance(tx, ty, both[0], both[1]);
        synchronized (this.lru) {
            this.lru.put(key, t == null ? MISSING : t);
        }
        return t;
    }

    // raw tile bytes, cached on disk per snapshot; falls back to the newest snapshot if the pinned one is gone
    private final TileCache<byte[]> rawTiles = new TileCache<>("osm-tiles", (z, x, y) ->
            "https://tiles.openfreemap.org/planet/" + this.snapshot() + "/" + z + "/" + x + "/" + y + ".pbf", b -> b, 16);

    private byte[] fetch(int tx, int ty) {
        return this.rawTiles.get(this.Z, tx, ty);
    }

    private final java.util.LinkedHashMap<Long, byte[][]> classCache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[][]> e) {
            return this.size() > 96;
        }
    };

    /** the water classes of one tile, or null if OSM has no such tile */
    private byte[] classes(int tx, int ty) {
        byte[][] r = this.rasters(tx, ty);
        return r == null ? null : r[0];
    }

    private static final byte[][] NO_RASTERS = new byte[0][];

    /** {water classes, way guide classes (or null)} of one tile, or null if OSM has no such tile */
    private byte[][] rasters(int tx, int ty) {
        int n = 1 << this.Z;
        tx = Math.floorMod(tx, n);
        if (ty < 0 || ty >= n) return null;
        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
        synchronized (this.classCache) {
            byte[][] c = this.classCache.get(key);
            if (c != null) return c == NO_RASTERS ? null : c;
        }
        byte[] bytes = this.fetch(tx, ty);
        byte[][] c = null;
        try {
            if (bytes != null) {
                Map<String, MvtReader.Layer> layers = MvtReader.read(bytes);
                c = new byte[][]{this.rasterise(layers, tx, ty), this.ways ? OsmWays.rasterise(layers, this.Z, ty, RES, this.blockMetersAtEquator) : null};
            }
        } catch (IOException e) {
            DataEnv.LOGGER.error("Bad OSM tile {}/{}/{}: {}", this.Z, tx, ty, e.toString());
        }
        synchronized (this.classCache) {
            this.classCache.put(key, c == null ? NO_RASTERS : c);
        }
        return c;
    }

    /** distance to shore measured across the tile and its 8 neighbours, so it doesn't jump at tile edges */
    private Tile withDistance(int tx, int ty, byte[] center, byte[] ways) {
        int W = RES * 3;
        float[] d = new float[W * W];
        final float INF = 1e9f, D = 1.4142f;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                byte[] c = (i == 0 && j == 0) ? center : this.classes(tx + i, ty + j);
                for (int y = 0; y < RES; y++) {
                    int row = (y + (j + 1) * RES) * W + (i + 1) * RES;
                    for (int x = 0; x < RES; x++) {
                        boolean wet = c == null ? center[Math.min(RES - 1, Math.max(0, x)) + y * RES] != LAND : c[x + y * RES] != LAND;
                        d[row + x] = wet ? INF : 0;
                    }
                }
            }
        }
        for (int y = 0; y < W; y++) for (int x = 0; x < W; x++) {
            int i = x + y * W;
            if (d[i] == 0) continue;
            float v = d[i];
            if (x > 0) v = Math.min(v, d[i - 1] + 1);
            if (y > 0) v = Math.min(v, d[i - W] + 1);
            if (x > 0 && y > 0) v = Math.min(v, d[i - W - 1] + D);
            if (x < W - 1 && y > 0) v = Math.min(v, d[i - W + 1] + D);
            d[i] = v;
        }
        for (int y = W - 1; y >= 0; y--) for (int x = W - 1; x >= 0; x--) {
            int i = x + y * W;
            if (d[i] == 0) continue;
            float v = d[i];
            if (x < W - 1) v = Math.min(v, d[i + 1] + 1);
            if (y < W - 1) v = Math.min(v, d[i + W] + 1);
            if (x < W - 1 && y < W - 1) v = Math.min(v, d[i + W + 1] + D);
            if (x > 0 && y < W - 1) v = Math.min(v, d[i + W - 1] + D);
            d[i] = v;
        }
        double latC = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (ty + 0.5) / (1 << this.Z)))));
        double pxMeters = GeoProjection.EQUATOR_CIRCUMFERENCE * Math.cos(Math.toRadians(latC)) / (1 << this.Z) / RES;
        short[] dist = new short[RES * RES];
        for (int y = 0; y < RES; y++) for (int x = 0; x < RES; x++) {
            float v = d[(x + RES) + (y + RES) * W];
            dist[x + y * RES] = (short) Math.min(30000, v >= INF ? 30000 : Math.round(v * pxMeters));
        }
        return new Tile(center, dist, ways);
    }

    private byte[] rasterise(Map<String, MvtReader.Layer> layers, int tx, int ty) {
        byte[] cls = new byte[RES * RES];
        int Z = this.Z;
        double latC = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (ty + 0.5) / (1 << Z)))));
        double tileMeters = GeoProjection.EQUATOR_CIRCUMFERENCE * Math.cos(Math.toRadians(latC)) / (1 << Z);
        double pxMeters = tileMeters / RES;
        double blockMeters = this.blockMetersAtEquator * Math.cos(Math.toRadians(latC));

        MvtReader.Layer water = layers.get("water");
        if (water != null) {
            double k = (double) RES / water.extent();
            for (MvtReader.Feature f : water.features()) {
                if (f.type() != MvtReader.POLYGON) continue;
                String c = f.str("class");
                if (c == null || c.equals("swimming_pool")) continue;
                Object inter = f.tags().get("intermittent");
                if (inter != null && !"0".equals(inter.toString()) && !"false".equals(inter.toString())) continue;
                byte v = switch (c) {
                    case "ocean" -> OCEAN;
                    case "river" -> RIVER;
                    default -> LAKE;          // lake, pond, dock, reservoir, basin...
                };
                fillPolygon(cls, f.rings(), k, v);
            }
        }
        MvtReader.Layer ways = layers.get("waterway");
        if (ways != null) {
            double k = (double) RES / ways.extent();
            for (MvtReader.Feature f : ways.features()) {
                if (f.type() != MvtReader.LINE) continue;
                String c = f.str("class"), brunnel = f.str("brunnel");
                if (c == null || "tunnel".equals(brunnel)) continue;
                Object inter = f.tags().get("intermittent");
                boolean intermittent = inter != null && !"0".equals(inter.toString()) && !"false".equals(inter.toString());
                double widthM;
                byte v;
                switch (c) {
                    case "river" -> { widthM = 14; v = RIVER; }
                    case "canal" -> { widthM = 10; v = RIVER; }
                    case "stream" -> {
                        if (intermittent || this.blockMetersAtEquator > 10) continue;   // too fine for small-scale worlds
                        widthM = 3;
                        v = STREAM;
                    }
                    default -> { continue; }                                          // ditches, drains
                }
                double w = Math.max(widthM, blockMeters * 1.05) / pxMeters;          // at least one block wide
                for (int[] line : f.rings()) drawLine(cls, line, k, w / 2.0, v);
            }
        }
        return cls;
    }

    /** even-odd scanline fill of all rings of one feature */
    private static void fillPolygon(byte[] cls, List<int[]> rings, double k, byte v) {
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int[] r : rings) for (int i = 1; i < r.length; i += 2) {
            minY = Math.min(minY, r[i] * k);
            maxY = Math.max(maxY, r[i] * k);
        }
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(RES - 1, (int) Math.ceil(maxY));
        double[] xs = new double[64];
        for (int py = y0; py <= y1; py++) {
            double sy = py + 0.5;
            int n = 0;
            for (int[] r : rings) {
                int pts = r.length / 2;
                for (int i = 0; i < pts; i++) {
                    int j = (i + 1) % pts;
                    double ay = r[2 * i + 1] * k, by = r[2 * j + 1] * k;
                    if ((ay <= sy) == (by <= sy)) continue;
                    double ax = r[2 * i] * k, bx = r[2 * j] * k;
                    if (n == xs.length) xs = java.util.Arrays.copyOf(xs, n * 2);
                    xs[n++] = ax + (sy - ay) / (by - ay) * (bx - ax);
                }
            }
            java.util.Arrays.sort(xs, 0, n);
            for (int i = 0; i + 1 < n; i += 2) {
                int a = Math.max(0, (int) Math.ceil(xs[i] - 0.5)), b = Math.min(RES - 1, (int) Math.floor(xs[i + 1] - 0.5));
                for (int px = a; px <= b; px++) {
                    int idx = px + py * RES;
                    if (cls[idx] == LAND || v == OCEAN) cls[idx] = v;
                }
            }
        }
    }

    private static void drawLine(byte[] cls, int[] line, double k, double radius, byte v) {
        int pts = line.length / 2;
        int r = (int) Math.ceil(radius);
        double r2 = radius * radius;
        for (int i = 0; i + 1 < pts; i++) {
            double ax = line[2 * i] * k, ay = line[2 * i + 1] * k, bx = line[2 * i + 2] * k, by = line[2 * i + 3] * k;
            int minX = Math.max(0, (int) Math.floor(Math.min(ax, bx) - r)), maxX = Math.min(RES - 1, (int) Math.ceil(Math.max(ax, bx) + r));
            int minY = Math.max(0, (int) Math.floor(Math.min(ay, by) - r)), maxY = Math.min(RES - 1, (int) Math.ceil(Math.max(ay, by) + r));
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    double cx = px + 0.5, cy = py + 0.5;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((cx - ax) * dx + (cy - ay) * dy) / len2));
                    double ex = ax + t * dx - cx, ey = ay + t * dy - cy;
                    if (ex * ex + ey * ey <= r2) {
                        int idx = px + py * RES;
                        if (cls[idx] == LAND) cls[idx] = v;
                    }
                }
            }
        }
    }
}
