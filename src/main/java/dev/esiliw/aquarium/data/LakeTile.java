package dev.esiliw.aquarium.data;

import java.util.ArrayDeque;

/**
 * Inland water for one land-cover tile: which pixels are water, one flat water level per lake
 * (the lowest point of its shoreline) and the distance of each water pixel from the shore.
 */
public final class LakeTile {
    public static final short NO_LEVEL = Short.MIN_VALUE;

    final int size;
    final boolean[] water;
    final short[] level;     // metres, per pixel (same for the whole lake)
    final float[] dist;      // distance to shore in pixels
    final boolean[] marine;  // land-cover water connected to the sea (lagoons, estuaries, misclassified coast)

    LakeTile(int size, boolean[] water, boolean[] marine, short[] level, float[] dist) {
        this.size = size;
        this.water = water;
        this.marine = marine;
        this.level = level;
        this.dist = dist;
    }

    public interface ElevationAtPixel {
        double get(int px, int py);
    }

    public interface OceanAtPixel {
        /** TRUE/FALSE from the coastline outlines, null if unknown */
        Boolean get(int px, int py);
    }

    static LakeTile build(TerrariumRasterReader.Raster cover, int waterId, ElevationAtPixel elevation, OceanAtPixel ocean) {
        int w = cover.width, h = cover.height;
        boolean[] water = new boolean[w * h];
        boolean[] marine = new boolean[w * h];
        for (int i = 0; i < water.length; i++) water[i] = cover.values[i] == waterId;
        short[] level = new short[w * h];
        java.util.Arrays.fill(level, NO_LEVEL);
        int[] comp = new int[w * h];
        int[] queue = new int[w * h];
        int nComp = 0;
        for (int start = 0; start < water.length; start++) {
            if (!water[start] || comp[start] != 0) continue;
            nComp++;
            int head = 0, tail = 0;
            queue[tail++] = start;
            comp[start] = nComp;
            double ringMin = Double.MAX_VALUE;
            java.util.HashSet<Integer> ringSeen = new java.util.HashSet<>();
            java.util.ArrayList<Double> ring = new java.util.ArrayList<>();
            while (head < tail) {
                int i = queue[head++];
                int x = i % w, y = i / w;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = nx + ny * w;
                    if (water[j]) {
                        if (comp[j] == 0) {
                            comp[j] = nComp;
                            queue[tail++] = j;
                        }
                    } else if (ringSeen.add(j)) {
                        double ev = elevation.get(nx, ny);
                        ring.add(ev);
                        ringMin = Math.min(ringMin, ev);
                    }
                }
            }
            // whole tile is water / no shore found: use the water pixels themselves
            if (ringMin == Double.MAX_VALUE) {
                for (int q = 0; q < tail; q++) ringMin = Math.min(ringMin, elevation.get(queue[q] % w, queue[q] / w));
            }
            // a low percentile of the shoreline, not the single lowest point (outlets / pits would drag the whole lake down)
            if (!ring.isEmpty()) {
                java.util.Collections.sort(ring);
                ringMin = ring.get(Math.min(ring.size() - 1, (int) (ring.size() * 0.2)));
            }
            // land-cover water that is really the sea (the outlines know the coast far better than 250 m pixels): not a lake
            if (ocean != null) {
                int samples = 0, sea = 0;
                int stride = Math.max(1, tail / 64);
                for (int q = 0; q < tail; q += stride) {
                    Boolean o = ocean.get(queue[q] % w, queue[q] / w);
                    samples++;
                    if (Boolean.TRUE.equals(o)) sea++;
                }
                if (sea > 0 && sea >= samples * 0.15) {
                    for (int q = 0; q < tail; q++) marine[queue[q]] = true;
                    continue;
                }
            }
            short lv = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, Math.floor(ringMin)));
            for (int q = 0; q < tail; q++) level[queue[q]] = lv;
        }
        // chamfer distance to the nearest non-water pixel
        float[] dist = new float[w * h];
        final float INF = 1e9f, D = 1.4142f;
        for (int i = 0; i < dist.length; i++) dist[i] = water[i] ? INF : 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = x + y * w;
            if (dist[i] == 0) continue;
            float v = dist[i];
            if (x > 0) v = Math.min(v, dist[i - 1] + 1);
            if (y > 0) v = Math.min(v, dist[i - w] + 1);
            if (x > 0 && y > 0) v = Math.min(v, dist[i - w - 1] + D);
            if (x < w - 1 && y > 0) v = Math.min(v, dist[i - w + 1] + D);
            dist[i] = v;
        }
        for (int y = h - 1; y >= 0; y--) for (int x = w - 1; x >= 0; x--) {
            int i = x + y * w;
            if (dist[i] == 0) continue;
            float v = dist[i];
            if (x < w - 1) v = Math.min(v, dist[i + 1] + 1);
            if (y < h - 1) v = Math.min(v, dist[i + w] + 1);
            if (x < w - 1 && y < h - 1) v = Math.min(v, dist[i + w + 1] + D);
            if (x > 0 && y < h - 1) v = Math.min(v, dist[i + w - 1] + D);
            dist[i] = v;
        }
        for (int i = 0; i < dist.length; i++) if (dist[i] >= INF) dist[i] = 50;
        return new LakeTile(w, water, marine, level, dist);
    }
}
