/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 * Original code (c) gegy, LGPL-3.0.
 */
package dev.esiliw.aquarium.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smooth, coarse sea-floor depth for the whole planet.
 * The elevation layer stores the sea as 0 m inside every land-elevation (NASADEM) tile, and only has real bathymetry
 * (GEBCO) outside them. This takes a coarse zoom of the elevation layer (~2 km pixels, 20 degree tiles), keeps only pixels
 * with a real depth and fills the gaps by push-pull interpolation, so every sea point gets a plausible depth.
 */
public final class BathymetryField {
    public static final int ZOOM = 2;
    private final GeoTileSource source = new GeoTileSource("elevation2", ZOOM, TerrariumRasterReader.FORMAT_SHORT, 4);
    private final Map<Long, float[]> filled = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
            return this.size() > 6;
        }
    };

    /** metres (negative), or NaN where the data is missing */
    public double depthAt(double lat, double lon) {
        int gw = (int) Math.round(GeoTileSource.globalWidth(ZOOM)), gh = (int) Math.round(GeoTileSource.globalHeight(ZOOM));
        double fx = GeoProjection.pixelX(lon, ZOOM) - 0.5, fy = GeoProjection.pixelY(lat, ZOOM) - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0, sum = 0, wsum = 0;
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                int px = Math.floorMod(x0 + i, gw), py = Math.max(0, Math.min(gh - 1, y0 + j));
                float[] t = this.tile(px / GeoTileSource.TILE_SIZE, py / GeoTileSource.TILE_SIZE);
                if (t == null) continue;
                double v = t[(px % GeoTileSource.TILE_SIZE) + (py % GeoTileSource.TILE_SIZE) * GeoTileSource.TILE_SIZE];
                if (Double.isNaN(v)) continue;
                double wgt = (i == 0 ? 1 - tx : tx) * (j == 0 ? 1 - ty : ty);
                sum += v * wgt;
                wsum += wgt;
            }
        }
        return wsum > 0 ? sum / wsum : Double.NaN;
    }

    /** raw (unfilled) coarse elevation in metres, nearest pixel */
    public double rawAt(double lat, double lon) {
        int gw = (int) Math.round(GeoTileSource.globalWidth(ZOOM)), gh = (int) Math.round(GeoTileSource.globalHeight(ZOOM));
        int px = Math.floorMod((int) Math.floor(GeoProjection.pixelX(lon, ZOOM)), gw);
        int py = Math.max(0, Math.min(gh - 1, (int) Math.floor(GeoProjection.pixelY(lat, ZOOM))));
        TerrariumRasterReader.Raster r = this.source.getTile(px / GeoTileSource.TILE_SIZE, py / GeoTileSource.TILE_SIZE);
        if (r == null) return Double.NaN;
        return r.get(px % GeoTileSource.TILE_SIZE, py % GeoTileSource.TILE_SIZE);
    }

    private float[] tile(int tx, int ty) {
        long key = ((long) tx << 32) | (ty & 0xFFFFFFFFL);
        synchronized (this.filled) {
            float[] f = this.filled.get(key);
            if (f != null) return f;
        }
        TerrariumRasterReader.Raster r = this.source.getTile(tx, ty);
        if (r == null) return null;
        float[] f = fill(r);
        synchronized (this.filled) {
            this.filled.put(key, f);
        }
        return f;
    }

    /** push-pull fill: pixels at or above -1 m count as unknown and get the smooth average of the real depths around them */
    static float[] fill(TerrariumRasterReader.Raster r) {
        int n = GeoTileSource.TILE_SIZE;
        int size = 1;
        while (size < n) size <<= 1;
        int levels = Integer.numberOfTrailingZeros(size) + 1;
        float[][] val = new float[levels][], wt = new float[levels][];
        val[0] = new float[size * size];
        wt[0] = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = Math.min(x, r.width - 1), sy = Math.min(y, r.height - 1);
                int v = r.get(sx, sy);
                if (v < -1) {
                    val[0][x + y * size] = v;
                    wt[0][x + y * size] = 1;
                }
            }
        }
        // push: average known values down the pyramid
        for (int l = 1, s = size >> 1; l < levels; l++, s >>= 1) {
            int ps = s << 1;
            val[l] = new float[s * s];
            wt[l] = new float[s * s];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    float vs = 0, ws = 0;
                    for (int k = 0; k < 4; k++) {
                        int i = (2 * x + (k & 1)) + (2 * y + (k >> 1)) * ps;
                        vs += val[l - 1][i] * wt[l - 1][i];
                        ws += wt[l - 1][i];
                    }
                    if (ws > 0) {
                        val[l][x + y * s] = vs / ws;
                        wt[l][x + y * s] = Math.min(1, ws);
                    }
                }
            }
        }
        // pull: unknown pixels take the bilinear upsample of the coarser level
        for (int l = levels - 2, s = 2; l >= 0; l--, s <<= 1) {
            int cs = s >> 1;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int i = x + y * s;
                    if (wt[l][i] >= 1) continue;
                    double cx = Math.max(0, Math.min(cs - 1, (x - 0.5) / 2.0)), cy = Math.max(0, Math.min(cs - 1, (y - 0.5) / 2.0));
                    int x0 = (int) cx, y0 = (int) cy, x1 = Math.min(cs - 1, x0 + 1), y1 = Math.min(cs - 1, y0 + 1);
                    double ax = cx - x0, ay = cy - y0;
                    double up = (val[l + 1][x0 + y0 * cs] * (1 - ax) + val[l + 1][x1 + y0 * cs] * ax) * (1 - ay)
                            + (val[l + 1][x0 + y1 * cs] * (1 - ax) + val[l + 1][x1 + y1 * cs] * ax) * ay;
                    float w = wt[l][i];
                    val[l][i] = (float) (val[l][i] * w + up * (1 - w));
                    wt[l][i] = 1;
                }
            }
        }
        boolean any = wt[levels - 1][0] > 0;
        float[] out = new float[n * n];
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) out[x + y * n] = any ? val[0][x + y * size] : Float.NaN;
        return out;
    }
}
