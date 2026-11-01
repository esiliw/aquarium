/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.util.List;
import java.util.Map;

/**
 * Building footprints and heights from OpenStreetMap (OpenMapTiles "building" layer, zoom 13+), rasterised per tile:
 * each pixel points at a building, each building has one ground elevation (at its centre) and a height, so its roof is flat.
 */
public final class OsmBuildings {
    public interface Elevation {
        double at(double lat, double lon);
    }

    /** idx: 0 = no building, else building number + 1; base = ground elevation (m) at the centre; height in metres */
    public record Raster(short[] idx, float[] base, float[] height) {
    }

    private OsmBuildings() {
    }

    static Raster rasterise(Map<String, MvtReader.Layer> layers, int z, int tx, int ty, int res, Elevation elevation) {
        MvtReader.Layer b = layers.get("building");
        if (b == null || b.features().isEmpty()) return null;
        short[] idx = new short[res * res];
        int count = Math.min(32000, b.features().size());
        float[] base = new float[count], height = new float[count];
        double k = (double) res / b.extent();
        int n = 0;
        for (MvtReader.Feature f : b.features()) {
            if (n >= count) break;
            if (f.type() != MvtReader.POLYGON || f.rings().isEmpty()) continue;
            Object hide = f.tags().get("hide_3d");
            if (hide != null && "true".equals(hide.toString())) continue;
            double h = number(f.tags().get("render_height"), 5.0);
            // centre of the outer ring, in tile units -> lat/lon -> ground elevation there
            int[] ring = f.rings().get(0);
            double sx = 0, sy = 0;
            int pts = ring.length / 2;
            for (int i = 0; i < pts; i++) {
                sx += ring[2 * i];
                sy += ring[2 * i + 1];
            }
            double u = sx / pts / b.extent(), v = sy / pts / b.extent();
            double lon = (tx + u) / (1 << z) * 360.0 - 180.0;
            double lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (ty + v) / (1 << z)))));
            base[n] = (float) elevation.at(lat, lon);
            height[n] = (float) Math.max(2.0, h);
            fill(idx, res, f.rings(), k, (short) (n + 1));
            n++;
        }
        if (n == 0) return null;
        return new Raster(idx, java.util.Arrays.copyOf(base, n), java.util.Arrays.copyOf(height, n));
    }

    private static double number(Object o, double fallback) {
        if (o instanceof Number num) return num.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** even-odd scanline fill of one feature's rings */
    private static void fill(short[] idx, int res, List<int[]> rings, double k, short v) {
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int[] r : rings) for (int i = 1; i < r.length; i += 2) {
            minY = Math.min(minY, r[i] * k);
            maxY = Math.max(maxY, r[i] * k);
        }
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(res - 1, (int) Math.ceil(maxY));
        double[] xs = new double[32];
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
                int a = Math.max(0, (int) Math.ceil(xs[i] - 0.5)), e = Math.min(res - 1, (int) Math.floor(xs[i + 1] - 0.5));
                for (int px = a; px <= e; px++) idx[px + py * res] = v;
            }
        }
    }
}
