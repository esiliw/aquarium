/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0.
 */
package dev.esiliw.aquarium.data;

import dev.esiliw.aquarium.world.Cover;

/**
 * Samples the Terrarium datasets at a lat/lon:
 * elevation (metres, NASADEM land + GEBCO bathymetry, zoom 0-6) and ESA CCI land cover (zoom 0-4).
 */
public final class EarthData {
    public static final int ELEVATION_MAX_ZOOM = 6;
    public static final int COVER_MAX_ZOOM = 4;

    /** where the data comes from: gegy's Terrarium server (worlds made before 0.4) or open public sources */
    public enum Source {
        TERRARIUM, OPEN
    }

    private static final java.util.Map<String, EarthData> SHARED = new java.util.concurrent.ConcurrentHashMap<>();

    /** One shared instance per world scale and source, so the generator and the biome source share tile caches. */
    public static EarthData forScale(double worldScale) {
        return get(worldScale, Source.TERRARIUM);
    }

    public static EarthData get(double worldScale, Source source) {
        return SHARED.computeIfAbsent(worldScale + "/" + source, k -> new EarthData(worldScale, source));
    }

    private final Source source;
    private final AwsElevation aws;          // OPEN: fine elevation + bathymetry
    private final AwsElevation awsCoarse;    // OPEN: coarse elevation for whole-cell decisions
    private final WorldCover worldCover;     // OPEN: 10 m land cover

    public Source source() {
        return this.source;
    }

    private final GeoTileSource elevation;
    private final GeoTileSource cover;
    private final GeoTileSource coarseElevation;      // elevation on the land-cover grid, for lake levels
    private final OceanPolygons ocean = new OceanPolygons();
    private static final BathymetryField BATHYMETRY = new BathymetryField();
    private final java.util.Map<Long, Boolean> emptyCellIsSea = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, LakeTile> lakes = new java.util.LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, LakeTile> e) {
            return this.size() > 12;
        }
    };

    private EarthData(double worldScale, Source source) {
        this.source = source;
        if (source == Source.OPEN) {
            this.aws = new AwsElevation(AwsElevation.zoomFor(worldScale), 256);
            this.awsCoarse = new AwsElevation(6, 16);
            this.worldCover = new WorldCover(worldScale);
        } else {
            this.aws = null;
            this.awsCoarse = null;
            this.worldCover = null;
        }
        this.elevation = new GeoTileSource("elevation2", GeoTileSource.zoomForScale(worldScale, 0, ELEVATION_MAX_ZOOM),
                TerrariumRasterReader.FORMAT_SHORT, 48);
        this.cover = new GeoTileSource("landcover", GeoTileSource.zoomForScale(worldScale, 0, COVER_MAX_ZOOM),
                TerrariumRasterReader.FORMAT_UBYTE, 24);
        this.coarseElevation = new GeoTileSource("elevation2", this.cover.zoom(), TerrariumRasterReader.FORMAT_SHORT, 8);
    }

    /** TRUE/FALSE from the ocean outlines, or null where there are none (use elevation instead). */
    public Boolean oceanAt(double lat, double lon) {
        Boolean o = this.ocean.oceanAt(lat, lon);
        if (o != null) return o;
        // a 1x1 degree cell without any coastline is either all sea or all land: decide once for the whole cell,
        // so low-lying land (deserts, polders, depressions) isn't flooded speck by speck
        int cx = (int) Math.floor(lon), cy = (int) Math.floor(lat);
        long key = ((long) cx << 32) | (cy & 0xFFFFFFFFL);
        return this.emptyCellIsSea.computeIfAbsent(key, k -> {
            int sea = 0, n = 0;
            for (int j = 0; j < 7; j++) {
                for (int i = 0; i < 7; i++) {
                    double la = cy + (j + 0.5) / 7.0, lo = cx + (i + 0.5) / 7.0;
                    double v = this.awsCoarse != null ? this.awsCoarse.elevation(la, lo) : BATHYMETRY.rawAt(la, lo);
                    if (Double.isNaN(v)) continue;
                    n++;
                    if (v < -1) sea++;
                }
            }
            return n > 0 && sea * 2 > n;
        });
    }

    /** smooth sea-floor depth (metres, negative) filled in where the elevation data has no bathymetry, NaN if unknown */
    public double bathymetry(double lat, double lon) {
        if (this.aws != null) return Double.NaN;          // the open elevation already has real sea depths everywhere
        return BATHYMETRY.depthAt(lat, lon);
    }

    /** metres to the nearest coastline from the outlines (capped) */
    public double coastDistance(double lat, double lon, double maxMeters) {
        return this.ocean.coastDistance(lat, lon, maxMeters);
    }

    /** metres per land-cover pixel at the equator */
    public double coverPixelMeters() {
        return GeoProjection.EQUATOR_CIRCUMFERENCE / GeoTileSource.globalWidth(this.cover.zoom());
    }

    private LakeTile lakeTile(int tx, int ty) {
        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
        synchronized (this.lakes) {
            LakeTile t = this.lakes.get(key);
            if (t != null) return t;
        }
        TerrariumRasterReader.Raster c = this.cover.getTile(tx, ty);
        if (c == null) return null;
        TerrariumRasterReader.Raster e = this.coarseElevation.getTile(tx, ty);
        int zoom = this.cover.zoom();
        LakeTile t = LakeTile.build(c, Cover.WATER.id, (px, py) -> e == null ? 0 : e.get(px, py),
                (px, py) -> this.ocean.oceanAt(GeoProjection.latOfPixel(ty * GeoTileSource.TILE_SIZE + py + 0.5, zoom),
                        GeoProjection.lonOfPixel(tx * GeoTileSource.TILE_SIZE + px + 0.5, zoom)));
        synchronized (this.lakes) {
            this.lakes.put(key, t);
        }
        return t;
    }

    /** Inland water at a point: fraction of water around it (0..1), the lake's flat level (m) and distance to shore (cover pixels). */
    public record Water(double fraction, double level, double shoreDistance, double marineFraction) {
        public static final Water NONE = new Water(0, 0, 0, 0);
    }

    public Water water(double lat, double lon) {
        int zoom = this.cover.zoom();
        int gw = (int) Math.round(GeoTileSource.globalWidth(zoom));
        int gh = (int) Math.round(GeoTileSource.globalHeight(zoom));
        double fx = GeoProjection.pixelX(lon, zoom) - 0.5, fy = GeoProjection.pixelY(lat, zoom) - 0.5;
        // same edge roughening as the land cover, so water bodies don't trace the square data pixels
        double jx = 0.45 * Noise.smooth(fx * 1.7, fy * 1.7, 15) + 0.2 * Noise.smooth(fx * 5.1, fy * 5.1, 16);
        double jy = 0.45 * Noise.smooth(fx * 1.7, fy * 1.7, 17) + 0.2 * Noise.smooth(fx * 5.1, fy * 5.1, 18);
        fx += jx;
        fy += jy;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double frac = 0, wsum = 0, level = Double.NaN, dist = 0, best = -1, marine = 0;
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                int px = Math.floorMod(x0 + i, gw), py = Math.max(0, Math.min(gh - 1, y0 + j));
                LakeTile t = this.lakeTile(px / GeoTileSource.TILE_SIZE, py / GeoTileSource.TILE_SIZE);
                double wgt = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
                wsum += wgt;
                if (t == null) continue;
                int k = (px % GeoTileSource.TILE_SIZE) + (py % GeoTileSource.TILE_SIZE) * t.size;
                if (t.water[k] && t.marine[k]) {
                    marine += wgt;
                } else if (t.water[k]) {
                    frac += wgt;
                    dist += wgt * t.dist[k];
                    if (wgt > best) {
                        best = wgt;
                        level = t.level[k];
                    }
                }
            }
        }
        if (frac <= 0 && marine <= 0) return Water.NONE;
        if (frac <= 0) return new Water(0, 0, 0, marine / wsum);
        return new Water(frac / wsum, level, dist / frac, marine / wsum);
    }

    private static int floorDiv(double v) {
        return (int) Math.floor(v / GeoTileSource.TILE_SIZE);
    }

    private int rawElevation(int px, int py) {
        int zoom = this.elevation.zoom();
        int w = (int) Math.round(GeoTileSource.globalWidth(zoom));
        int h = (int) Math.round(GeoTileSource.globalHeight(zoom));
        px = Math.floorMod(px, w);                      // wrap around the date line
        py = Math.max(0, Math.min(h - 1, py));
        TerrariumRasterReader.Raster t = this.elevation.getTile(px / GeoTileSource.TILE_SIZE, py / GeoTileSource.TILE_SIZE);
        if (t == null) return -1;
        return t.get(px % GeoTileSource.TILE_SIZE, py % GeoTileSource.TILE_SIZE);
    }

    /** Elevation in metres, bicubic (Catmull-Rom) interpolation between data pixels. */
    public double elevation(double lat, double lon) {
        if (this.aws != null) return this.aws.elevation(lat, lon);
        int zoom = this.elevation.zoom();
        double fx = GeoProjection.pixelX(lon, zoom) - 0.5;
        double fy = GeoProjection.pixelY(lat, zoom) - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double[] col = new double[4];
        for (int j = -1; j <= 2; j++) {
            double p0 = this.rawElevation(x0 - 1, y0 + j), p1 = this.rawElevation(x0, y0 + j);
            double p2 = this.rawElevation(x0 + 1, y0 + j), p3 = this.rawElevation(x0 + 2, y0 + j);
            col[j + 1] = cubic(p0, p1, p2, p3, tx);
        }
        return cubic(col[0], col[1], col[2], col[3], ty);
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 + t * (3.0 * (p1 - p2) + p3 - p0)));
    }

    public Cover cover(double lat, double lon) {
        if (this.worldCover != null) return this.worldCover.cover(lat, lon);
        int zoom = this.cover.zoom();
        int w = (int) Math.round(GeoTileSource.globalWidth(zoom));
        int h = (int) Math.round(GeoTileSource.globalHeight(zoom));
        double fx = GeoProjection.pixelX(lon, zoom), fy = GeoProjection.pixelY(lat, zoom);
        // roughen the borders between land-cover pixels so areas aren't perfect squares
        fx += 0.45 * Noise.smooth(fx * 1.7, fy * 1.7, 11) + 0.2 * Noise.smooth(fx * 5.1, fy * 5.1, 12);
        fy += 0.45 * Noise.smooth(fx * 1.7, fy * 1.7, 13) + 0.2 * Noise.smooth(fx * 5.1, fy * 5.1, 14);
        int px = Math.floorMod((int) Math.floor(fx), w);
        int py = Math.max(0, Math.min(h - 1, (int) Math.floor(fy)));
        TerrariumRasterReader.Raster t = this.cover.getTile(px / GeoTileSource.TILE_SIZE, py / GeoTileSource.TILE_SIZE);
        if (t == null) return Cover.NO;
        return Cover.byId(t.get(px % GeoTileSource.TILE_SIZE, py % GeoTileSource.TILE_SIZE));
    }

    public int elevationZoom() {
        if (this.aws != null) return this.aws.zoom();
        return this.elevation.zoom();
    }

    public int coverZoom() {
        return this.cover.zoom();
    }
}
