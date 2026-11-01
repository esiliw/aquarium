/*
 * Aquarium - a Fabric port of Terrarium (https://github.com/gegy1000/Terrarium)
 */
package dev.esiliw.aquarium.data;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Elevation and bathymetry from the Tilezen/Mapzen terrain tiles on AWS Open Data (public, no key).
 * 256 px Web Mercator PNG tiles, "terrarium" encoding: metres = R*256 + G + B/256 - 32768. Zoom 0..15.
 * Sources merged by Tilezen: SRTM, GMTED, NED/3DEP, ETOPO1/GEBCO bathymetry, national LIDAR where available.
 */
public final class AwsElevation {
    public static final int MAX_ZOOM = 15;
    public static final int SIZE = 256;
    private final int zoom;
    private final TileCache<float[]> tiles;

    public AwsElevation(int zoom, int memoryTiles) {
        this.zoom = zoom;
        this.tiles = new TileCache<>("aws-terrain", (z, x, y) -> "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/" + z + "/" + x + "/" + y + ".png",
                AwsElevation::decode, memoryTiles);
    }

    /** zoom whose pixels are no larger than one block (blocks and pixels shrink alike with latitude in Mercator) */
    public static int zoomFor(double metersPerBlockAtEquator) {
        double z = Math.log(GeoProjection.EQUATOR_CIRCUMFERENCE / SIZE / metersPerBlockAtEquator) / Math.log(2);
        return Math.max(0, Math.min(MAX_ZOOM, (int) Math.ceil(z - 1e-6)));
    }

    public int zoom() {
        return this.zoom;
    }

    static float[] decode(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IOException("not an image");
        float[] out = new float[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = img.getRGB(Math.min(x, img.getWidth() - 1), Math.min(y, img.getHeight() - 1));
                out[x + y * SIZE] = (((rgb >> 16) & 255) * 256 + ((rgb >> 8) & 255) + (rgb & 255) / 256f) - 32768f;
            }
        }
        fillHoles(out);
        despike(out);
        return out;
    }

    /** deeper than the deepest trench or higher than Everest: a "no data" pixel (black = -32768 m), not real ground */
    static boolean invalid(float v) {
        return !(v > -11500f && v < 9000f);
    }

    /** fills "no data" pixels from their valid neighbours, so a hole never becomes a bottomless trench */
    static void fillHoles(float[] t) {
        int holes = 0;
        for (float v : t) if (invalid(v)) holes++;
        if (holes == 0) return;
        if (holes == t.length) {
            java.util.Arrays.fill(t, 0f);
            return;
        }
        boolean[] bad = new boolean[t.length];
        for (int i = 0; i < t.length; i++) bad[i] = invalid(t[i]);
        while (holes > 0) {
            boolean[] next = bad.clone();
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int i = x + y * SIZE;
                if (!bad[i]) continue;
                double sum = 0;
                int n = 0;
                if (x > 0 && !bad[i - 1]) { sum += t[i - 1]; n++; }
                if (x < SIZE - 1 && !bad[i + 1]) { sum += t[i + 1]; n++; }
                if (y > 0 && !bad[i - SIZE]) { sum += t[i - SIZE]; n++; }
                if (y < SIZE - 1 && !bad[i + SIZE]) { sum += t[i + SIZE]; n++; }
                if (n > 0) {
                    t[i] = (float) (sum / n);
                    next[i] = false;
                    holes--;
                }
            }
            bad = next;
        }
    }

    /**
     * removes seams in the source data: lines a pixel or two wide that sit far below the ground on both sides,
     * like the crack along 28 N through the Everest massif where two survey tiles meet. A real cliff drops on one side only; sharp peaks are left alone.
     */
    static void despike(float[] t) {
        final int d = 3;
        final float limit = 150f;
        float[] src = t.clone();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int i = x + y * SIZE;
            float v = src[i];
            int ya = Math.max(0, y - d), yb = Math.min(SIZE - 1, y + d), xa = Math.max(0, x - d), xb = Math.min(SIZE - 1, x + d);
            if (ya < y && yb > y) {
                float a = src[x + ya * SIZE], b = src[x + yb * SIZE];
                if (v < Math.min(a, b) - limit) {
                    t[i] = a + (b - a) * (y - ya) / (float) (yb - ya);
                    continue;
                }
            }
            if (xa < x && xb > x) {
                float a = src[xa + y * SIZE], b = src[xb + y * SIZE];
                if (v < Math.min(a, b) - limit) t[i] = a + (b - a) * (x - xa) / (float) (xb - xa);
            }
        }
    }

    public static double pixelX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * SIZE * (1 << zoom);
    }

    public static double pixelY(double lat, int zoom) {
        double phi = Math.toRadians(Math.max(-GeoProjection.MERCATOR_MAX_LAT, Math.min(GeoProjection.MERCATOR_MAX_LAT, lat)));
        return (1.0 - Math.log(Math.tan(phi) + 1.0 / Math.cos(phi)) / Math.PI) / 2.0 * SIZE * (1 << zoom);
    }

    double raw(int px, int py) {
        int n = SIZE << this.zoom;
        px = Math.floorMod(px, n);
        py = Math.max(0, Math.min(n - 1, py));
        float[] t = this.tiles.get(this.zoom, px / SIZE, py / SIZE);
        if (t == null) return 0;
        return t[(px % SIZE) + (py % SIZE) * SIZE];
    }

    /** lowest raw pixel within r pixels: the flat surface of a lake or river next to its banks */
    public double minAround(double lat, double lon, int r) {
        int cx = (int) Math.floor(pixelX(lon, this.zoom)), cy = (int) Math.floor(pixelY(lat, this.zoom));
        double m = Double.MAX_VALUE;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) m = Math.min(m, this.raw(cx + dx, cy + dy));
        return m;
    }

    /** metres, bicubic between pixels */
    public double elevation(double lat, double lon) {
        double fx = pixelX(lon, this.zoom) - 0.5, fy = pixelY(lat, this.zoom) - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        double[] col = new double[4];
        for (int j = -1; j <= 2; j++) {
            col[j + 1] = cubic(this.raw(x0 - 1, y0 + j), this.raw(x0, y0 + j), this.raw(x0 + 1, y0 + j), this.raw(x0 + 2, y0 + j), tx);
        }
        return cubic(col[0], col[1], col[2], col[3], ty);
    }

    static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 + t * (3.0 * (p1 - p2) + p3 - p0)));
    }
}
