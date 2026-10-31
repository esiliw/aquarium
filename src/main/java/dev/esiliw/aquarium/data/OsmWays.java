/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import java.util.List;
import java.util.Map;

/**
 * Roads, railways and stations from OpenStreetMap (OpenMapTiles "transportation" and "poi" layers), drawn as a guide
 * on the ground: they follow the terrain as it is, nothing is graded. Higher codes win where lines overlap.
 */
public final class OsmWays {
    public static final byte NONE = 0, MINOR = 1, SECONDARY = 2, MAJOR = 3, RAIL_BED = 4, TRAM = 5, RAIL_LINE = 6, STATION = 7;

    private OsmWays() {
    }

    static byte[] rasterise(Map<String, MvtReader.Layer> layers, int z, int ty, int res, double blockMetersAtEquator) {
        byte[] out = new byte[res * res];
        double latC = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (ty + 0.5) / (1 << z)))));
        double cos = Math.cos(Math.toRadians(latC));
        double pxMeters = GeoProjection.EQUATOR_CIRCUMFERENCE * cos / (1 << z) / res;
        double block = blockMetersAtEquator * cos;
        boolean smallStreets = blockMetersAtEquator <= 12;

        MvtReader.Layer t = layers.get("transportation");
        if (t != null) {
            double k = (double) res / t.extent();
            for (MvtReader.Feature f : t.features()) {
                if (f.type() != MvtReader.LINE) continue;
                String cls = f.str("class"), sub = f.str("subclass"), brunnel = f.str("brunnel");
                if (cls == null || "tunnel".equals(brunnel)) continue;
                byte code;
                double widthM;
                switch (cls) {
                    case "motorway" -> { code = MAJOR; widthM = 25; }
                    case "trunk" -> { code = MAJOR; widthM = 18; }
                    case "primary" -> { code = SECONDARY; widthM = 12; }
                    case "secondary" -> { code = SECONDARY; widthM = 10; }
                    case "tertiary" -> { code = MINOR; widthM = 8; }
                    case "minor" -> {
                        if (!smallStreets) continue;
                        code = MINOR;
                        widthM = 6;
                    }
                    case "service" -> {
                        if (!smallStreets) continue;
                        code = MINOR;
                        widthM = 4;
                    }
                    case "rail" -> {
                        // a gravel bed for every track, with a one-block centre line on top
                        for (int[] line : f.rings()) draw(out, res, line, k, Math.max(6.0, block * 3.05) / pxMeters / 2, RAIL_BED);
                        for (int[] line : f.rings()) draw(out, res, line, k, block * 0.55 / pxMeters, RAIL_LINE);
                        continue;
                    }
                    case "transit" -> {
                        if ("subway".equals(sub) && !"bridge".equals(brunnel)) continue;   // subways are mostly underground
                        code = TRAM;
                        widthM = 0;
                    }
                    default -> { continue; }                                         // tracks, paths, construction, ferries
                }
                double radius = Math.max(widthM, block * 1.05) / pxMeters / 2;
                for (int[] line : f.rings()) draw(out, res, line, k, radius, code);
            }
        }
        MvtReader.Layer poi = layers.get("poi");
        if (poi != null) {
            double k = (double) res / poi.extent();
            for (MvtReader.Feature f : poi.features()) {
                if (f.type() != MvtReader.POINT || !"railway".equals(f.str("class"))) continue;
                String sub = f.str("subclass");
                if (!"station".equals(sub) && !"halt".equals(sub)) continue;
                for (int[] p : f.rings()) for (int i = 0; i + 1 < p.length; i += 2) {
                    draw(out, res, new int[]{p[i], p[i + 1]}, k, Math.max(8.0, block * 2.6) / pxMeters, STATION);
                }
            }
        }
        return out;
    }

    private static void draw(byte[] out, int res, int[] line, double k, double radius, byte code) {
        int pts = line.length / 2;
        double r2 = radius * radius;
        int r = (int) Math.ceil(radius);
        for (int i = 0; i < Math.max(1, pts - 1); i++) {
            double ax = line[2 * i] * k, ay = line[2 * i + 1] * k;
            double bx = pts > 1 ? line[2 * i + 2] * k : ax, by = pts > 1 ? line[2 * i + 3] * k : ay;
            int minX = Math.max(0, (int) Math.floor(Math.min(ax, bx) - r)), maxX = Math.min(res - 1, (int) Math.ceil(Math.max(ax, bx) + r));
            int minY = Math.max(0, (int) Math.floor(Math.min(ay, by) - r)), maxY = Math.min(res - 1, (int) Math.ceil(Math.max(ay, by) + r));
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    double cx = px + 0.5, cy = py + 0.5;
                    double tt = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((cx - ax) * dx + (cy - ay) * dy) / len2));
                    double ex = ax + tt * dx - cx, ey = ay + tt * dy - cy;
                    if (ex * ex + ey * ey <= r2) {
                        int idx = px + py * res;
                        if (out[idx] < code) out[idx] = code;
                    }
                }
            }
        }
    }
}
